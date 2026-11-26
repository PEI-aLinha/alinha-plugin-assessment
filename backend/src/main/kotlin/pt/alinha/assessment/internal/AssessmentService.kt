package pt.alinha.assessment.internal

import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.assessment.events.AssessmentEvaluated
import pt.alinha.assessment.events.AssessmentSubmitted
import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.assessment.internal.domain.Questionnaire
import pt.alinha.assessment.internal.domain.ScopeRules
import pt.alinha.assessment.internal.persistence.AnswerEntity
import pt.alinha.assessment.internal.persistence.AnswerRepository
import pt.alinha.assessment.internal.persistence.AssessmentEntity
import pt.alinha.assessment.internal.persistence.AssessmentRepository
import pt.alinha.assessment.internal.persistence.ResultEntity
import pt.alinha.assessment.internal.persistence.ResultRepository
import pt.alinha.assessment.internal.persistence.StoredControl
import pt.alinha.assessment.internal.persistence.complianceLevel
import pt.alinha.assessment.internal.persistence.levelId
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.compliance.Answer
import pt.alinha.core.compliance.ApplicabilityInput
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.CatalogueRegistry
import pt.alinha.core.compliance.ComplianceEngine
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.ControlStatus
import pt.alinha.core.compliance.IncompleteAnswersException
import pt.alinha.core.tenant.CorrelationContext
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
    private val applicability: ApplicabilityRules,
    private val audit: AuditTrail,
    private val json: JsonMapper,
    private val results: ResultRepository,
    private val engine: ComplianceEngine,
    private val tenants: TenantContext,
    private val correlation: CorrelationContext,
    private val events: ApplicationEventPublisher,
    transactionManager: PlatformTransactionManager,
) {
    /** Reads the winner of a concurrent start: the current transaction is already broken by then. */
    private val freshRead = TransactionTemplate(transactionManager).apply {
        propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW
        isReadOnly = true
    }

    /**
     * Starts a draft assessment of [framework] on its newest catalogue, cut to the scope the organization declared.
     * The declared [scope] is stored as sent; the audit entry has the outcome only (no company figures).
     */
    @Transactional
    fun start(framework: String, scope: ApplicabilityInput): AssessmentDetail {
        if (!FRAMEWORK_ID.matches(framework)) throw InvalidFrameworkException(framework)
        val resolved = ScopeRules.resolve(framework, applicability.assess(scope))
        val catalogue = catalogues.latest(framework) ?: throw CatalogueNotFoundException(framework)
        openAssessment(framework)?.let { throw AssessmentAlreadyOpenException(it) }

        val entity = AssessmentEntity(
            id = UUID.randomUUID(),
            framework = framework,
            catalogueVersion = catalogue.version,
            maxLevel = resolved.maxLevel,
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
                "maxLevel" to resolved.maxLevel.name,
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
        val questionnaires = mutableMapOf<Triple<String, Int, ComplianceLevel>, Questionnaire>()
        val items = result.content.map { assessment ->
            val questionnaire = questionnaires.getOrPut(
                Triple(assessment.framework, assessment.catalogueVersion, assessment.maxLevel),
            ) { questionnaire(assessment) }
            toSummary(assessment, progress(answered[assessment.id].orEmpty(), questionnaire))
        }
        return AssessmentPage(items, request.pageNumber, request.pageSize, result.totalElements)
    }

    /**
     * Freezes the answers and has the Compliance Engine compute the results (assessment#5, FR10): draft -> submitted
     * -> evaluated in this one transaction, each step audited (RNF04) and published as an event. The row lock makes
     * a concurrent answer or second submit wait, and then fail on the status.
     */
    @Transactional
    fun submit(id: UUID): AssessmentSummary {
        val assessment = assessments.findForUpdateById(id) ?: throw AssessmentNotFoundException()
        if (!assessment.status.acceptsAnswers) throw AssessmentNotEditableException(assessment.status)
        val questionnaire = questionnaire(assessment)
        val saved = answers.findAllByAssessmentId(id).associate { it.questionId to it.value }
        val evaluation = try {
            engine.evaluate(questionnaire.catalogue, assessment.maxLevel, saved)
        } catch (e: IncompleteAnswersException) {
            throw AssessmentIncompleteException(e.unanswered)
        }

        val now = now()
        val tenant = tenants.current()
        val progress = progress(saved.keys, questionnaire)
        assessment.moveTo(AssessmentStatus.SUBMITTED, now)
        audit.record(
            "assessment.submitted", ENTITY_TYPE, id.toString(),
            mapOf("answered" to progress.answered, "questions" to progress.total),
        )
        events.publishEvent(AssessmentSubmitted(tenant, correlation.current(), id, now))

        results.save(
            ResultEntity(
                id = UUID.randomUUID(),
                assessmentId = id,
                engineVersion = evaluation.engineVersion,
                currentLevel = evaluation.currentLevel?.levelId,
                targetLevel = evaluation.targetLevel.levelId,
                coverage = evaluation.coverage,
                controls = json.writeValueAsString(
                    evaluation.controls.map { StoredControl(it.controlId, it.level.levelId, it.status.name) },
                ),
                evaluatedAt = now,
            ),
        )
        assessment.moveTo(AssessmentStatus.EVALUATED, now)
        audit.record(
            "assessment.evaluated", ENTITY_TYPE, id.toString(),
            mapOf(
                "engineVersion" to evaluation.engineVersion,
                "currentLevel" to evaluation.currentLevel?.levelId,
                "targetLevel" to evaluation.targetLevel.levelId,
                "coverage" to evaluation.coverage,
            ),
        )
        events.publishEvent(
            AssessmentEvaluated(
                tenant, correlation.current(), id, assessment.framework, assessment.catalogueVersion, now,
            ),
        )
        return toSummary(assessment, progress)
    }

    /**
     * The stored results, with the control titles from the assessment's pinned catalogue version. A stored control
     * that is not in the questionnaire keeps its id as the title, rather than failing the whole page.
     */
    @Transactional(readOnly = true)
    fun results(id: UUID): AssessmentResults {
        val assessment = assessments.findOneById(id) ?: throw AssessmentNotFoundException()
        val result = results.findOneByAssessmentId(id) ?: throw ResultsNotAvailableException()
        val titles = questionnaire(assessment).controls.associate { it.id to it.title }
        val controls = json.readValue(result.controls, Array<StoredControl>::class.java).map {
            ControlResult(
                controlId = it.controlId,
                title = titles[it.controlId] ?: it.controlId,
                level = complianceLevel(it.level),
                status = ControlStatus.valueOf(it.status),
            )
        }
        return AssessmentResults(
            assessmentId = id,
            evaluatedAt = result.evaluatedAt,
            engineVersion = result.engineVersion,
            currentLevel = result.currentLevel?.let(::complianceLevel),
            targetLevel = complianceLevel(result.targetLevel),
            coverage = result.coverage,
            controls = controls,
        )
    }

    private fun toSummary(assessment: AssessmentEntity, progress: ProgressView) = AssessmentSummary(
        id = assessment.id,
        framework = assessment.framework,
        catalogueVersion = assessment.catalogueVersion,
        status = assessment.status.apiValue(),
        progress = progress,
        createdAt = assessment.createdAt,
        updatedAt = assessment.updatedAt,
    )

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
            rulesVersion = assessment.rulesVersion,
            progress = progress(saved.keys, questionnaire),
            createdAt = assessment.createdAt,
            updatedAt = assessment.updatedAt,
            controls = controls,
        )
    }
}
