package pt.alinha.assessment.internal

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.assessment.internal.domain.Questionnaire
import pt.alinha.assessment.internal.domain.ScopeRules
import pt.alinha.assessment.internal.persistence.AnswerEntity
import pt.alinha.assessment.internal.persistence.AnswerRepository
import pt.alinha.assessment.internal.persistence.AssessmentEntity
import pt.alinha.assessment.internal.persistence.AssessmentRepository
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.compliance.Answer
import pt.alinha.core.compliance.ApplicabilityQuery
import pt.alinha.core.compliance.CatalogueRegistry
import pt.alinha.core.tenant.TenantContext
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

private const val ENTITY_TYPE = "assessment"

/** ADR 0003: at most 100 items per page. */
private const val MAX_PAGE_SIZE = 100

/** PostgreSQL keeps microseconds: a response must show the same instant a later read returns. */
private fun now(): Instant = Instant.now().truncatedTo(ChronoUnit.MICROS)

/** Same pattern as the core's CatalogueLoader and the contract's StartAssessmentRequest.framework. */
private val FRAMEWORK_ID = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")

/**
 * Assessments of the current organization (assessment#2). Every query goes through the repositories, so it only
 * sees the current tenant; every write is audited in the same transaction (RNF04).
 */
@Service
class AssessmentService(
    private val assessments: AssessmentRepository,
    private val answers: AnswerRepository,
    private val catalogues: CatalogueRegistry,
    private val applicability: ApplicabilityQuery,
    private val tenants: TenantContext,
    private val audit: AuditTrail,
    private val json: JsonMapper,
    transactionManager: PlatformTransactionManager,
) {
    /** Reads the winner of a concurrent start: the current transaction is already broken by then. */
    private val freshRead = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    /**
     * Starts a draft assessment of [framework] on its newest catalogue, cut to the scope the organization answered in
     * the organization plugin (with the official CNCS qualification when registered). That applicability result is
     * stored with the assessment; the audit entry has the outcome only (no company figures).
     */
    @Transactional
    fun start(framework: String): AssessmentDetail {
        if (!FRAMEWORK_ID.matches(framework)) throw InvalidFrameworkException(framework)
        val scope = applicability.current(tenants.current()) ?: throw ScopeNotAnsweredException()
        val resolved = ScopeRules.resolve(framework, scope)
        val catalogue = catalogues.latest(framework) ?: throw CatalogueNotFoundException(framework)
        openAssessment(framework)?.let { throw AssessmentAlreadyOpenException(it) }
        val maxLevel = resolved.maxLevel ?: catalogue.levels.last().id
        check(catalogue.levelIndex(maxLevel) >= 0) {
            "Level '$maxLevel' is not in the scale of $framework v${catalogue.version}"
        }

        val entity = AssessmentEntity(
            id = UUID.randomUUID(),
            framework = framework,
            catalogueVersion = catalogue.version,
            maxLevel = maxLevel,
            nis2Classification = resolved.nis2Classification,
            rulesVersion = resolved.rulesVersion,
            scopeInput = json.writeValueAsString(scope),
            createdAt = now(),
        )
        val saved = try {
            assessments.saveAndFlush(entity)
        } catch (e: DataIntegrityViolationException) {
            // Another start of the same framework committed first (assessment_open_uq).
            val winner = freshRead.execute { openAssessment(framework) } ?: throw e
            throw AssessmentAlreadyOpenException(winner)
        }

        audit.record(
            "assessment.started", ENTITY_TYPE, saved.id.toString(),
            mapOf(
                "framework" to framework,
                "catalogueVersion" to catalogue.version,
                "maxLevel" to maxLevel,
                "nis2Classification" to resolved.nis2Classification.name,
                "rulesVersion" to resolved.rulesVersion,
            ),
        )
        return toDetail(saved)
    }

    /** The assessment with its questions and the answers saved so far. */
    @Transactional(readOnly = true)
    fun detail(id: UUID): AssessmentDetail =
        toDetail(assessments.findOneById(id) ?: throw AssessmentNotFoundException())

    /** The questions of [assessment], rebuilt from its pinned catalogue version (never copied to the database). */
    internal fun questionnaire(assessment: AssessmentEntity): Questionnaire {
        val catalogue = catalogues.get(assessment.framework, assessment.catalogueVersion)
            ?: error(
                "Catalogue ${assessment.framework} v${assessment.catalogueVersion} is missing: " +
                    "a published version must never be removed (RNF18)",
            )
        return Questionnaire(catalogue, assessment.maxLevel)
    }

    /** The organization's assessments, the most recently worked on first, so the user can resume where they stopped. */
    @Transactional(readOnly = true)
    fun list(page: Int, pageSize: Int): AssessmentPage {
        if (page < 0) throw InvalidPageException("page")
        if (pageSize !in 1..MAX_PAGE_SIZE) throw InvalidPageException("pageSize")
        val request = PageRequest.of(page, pageSize, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.asc("id")))
        val result = assessments.findAllBy(request)
        // One query for the answers of the whole page, and one questionnaire per catalogue version and level.
        val answered = answers.findAllByAssessmentIdIn(result.content.map { it.id }).groupBy({ it.assessmentId }, { it.questionId })
        val questionnaires = mutableMapOf<Triple<String, Int, String>, Questionnaire>()
        val items = result.content.map { assessment ->
            val questionnaire = questionnaires.getOrPut(
                Triple(assessment.framework, assessment.catalogueVersion, assessment.maxLevel),
            ) { questionnaire(assessment) }
            AssessmentSummary(
                id = assessment.id,
                framework = assessment.framework,
                catalogueVersion = assessment.catalogueVersion,
                status = assessment.status.apiValue(),
                progress = progress(answered[assessment.id].orEmpty(), questionnaire),
                createdAt = assessment.createdAt,
                updatedAt = assessment.updatedAt,
            )
        }
        return AssessmentPage(items, request.pageNumber, request.pageSize, result.totalElements)
    }

    /**
     * Saves one answer (autosave). Sending the value already saved changes nothing and writes no audit entry; every
     * real change is audited with the previous and the new value (RNF04).
     */
    @Transactional
    fun saveAnswer(id: UUID, questionId: String, answer: Answer): SavedAnswer {
        // The row lock makes answers (and, later, the submit) of one assessment run one at a time.
        val assessment = assessments.findForUpdateById(id) ?: throw AssessmentNotFoundException()
        if (!assessment.status.acceptsAnswers) throw AssessmentNotEditableException(assessment.status)
        val questionnaire = questionnaire(assessment)
        val question = questionnaire.question(questionId) ?: throw QuestionNotInAssessmentException(questionId)
        if (answer !in question.answers) throw AnswerNotOfferedException(questionId, answer)

        val now = now()
        val existing = answers.findOneByAssessmentIdAndQuestionId(id, questionId)
        val current = if (existing == null) {
            answers.save(AnswerEntity(UUID.randomUUID(), id, questionId, answer, now))
                .also { changed(assessment, questionId, previous = null, answer, now) }
        } else {
            existing.change(answer, now)?.let { previous -> changed(assessment, questionId, previous, answer, now) }
            existing
        }
        val progress = progress(answers.findAllByAssessmentId(id).map { it.questionId }, questionnaire)
        return SavedAnswer(questionId, current.value, current.answeredAt, progress)
    }

    /** Audit details hold the question id and status codes only, never free text (AuditTrail rule). */
    private fun changed(assessment: AssessmentEntity, questionId: String, previous: Answer?, answer: Answer, now: Instant) {
        assessment.touch(now)
        audit.record(
            "assessment.answer.saved", ENTITY_TYPE, assessment.id.toString(),
            mapOf("questionId" to questionId, "from" to previous?.name, "to" to answer.name),
        )
    }

    /** Only answers to questions of this questionnaire count. */
    private fun progress(answeredQuestionIds: Collection<String>, questionnaire: Questionnaire) =
        ProgressView(answeredQuestionIds.count { questionnaire.question(it) != null }, questionnaire.questionCount)

    private fun openAssessment(framework: String): UUID? =
        assessments.findFirstByFrameworkAndStatusNot(framework, AssessmentStatus.VALIDATED)?.id

    private fun toDetail(assessment: AssessmentEntity): AssessmentDetail {
        val questionnaire = questionnaire(assessment)
        val saved = answers.findAllByAssessmentId(assessment.id).associateBy { it.questionId }
        val controls = questionnaire.controls.map { control ->
            ControlView(
                id = control.id,
                title = control.title,
                level = control.level,
                scaleLevel = questionnaire.catalogue.levels[questionnaire.catalogue.levelIndex(control.level)],
                technical = control.technical,
                questions = control.questions.map { question ->
                    val answer = saved[question.id]
                    QuestionView(
                        id = question.id,
                        text = question.text,
                        explanation = question.explanation,
                        example = question.example,
                        options = question.answers,
                        answer = answer?.value,
                        answeredAt = answer?.answeredAt,
                    )
                },
            )
        }
        return AssessmentDetail(
            id = assessment.id,
            framework = assessment.framework,
            catalogueVersion = assessment.catalogueVersion,
            catalogueTitle = questionnaire.catalogue.title,
            status = assessment.status.apiValue(),
            nis2Classification = assessment.nis2Classification.apiValue(),
            maxLevel = assessment.maxLevel,
            maxScaleLevel = questionnaire.maxScaleLevel,
            rulesVersion = assessment.rulesVersion,
            progress = progress(saved.keys, questionnaire),
            createdAt = assessment.createdAt,
            updatedAt = assessment.updatedAt,
            controls = controls,
        )
    }
}
