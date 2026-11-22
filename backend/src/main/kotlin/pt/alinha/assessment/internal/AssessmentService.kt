package pt.alinha.assessment.internal

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.assessment.internal.domain.Questionnaire
import pt.alinha.assessment.internal.domain.ScopeRules
import pt.alinha.assessment.internal.persistence.AnswerRepository
import pt.alinha.assessment.internal.persistence.AssessmentEntity
import pt.alinha.assessment.internal.persistence.AssessmentRepository
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.compliance.ApplicabilityInput
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.CatalogueRegistry
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

private const val ENTITY_TYPE = "assessment"

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
            createdAt = Instant.now(),
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
            // Only answers to questions of this questionnaire count.
            progress = ProgressView(saved.keys.count { questionnaire.question(it) != null }, questionnaire.questionCount),
            createdAt = assessment.createdAt,
            updatedAt = assessment.updatedAt,
            controls = controls,
        )
    }
}
