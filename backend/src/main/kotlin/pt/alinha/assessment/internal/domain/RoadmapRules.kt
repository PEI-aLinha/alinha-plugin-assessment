package pt.alinha.assessment.internal.domain

import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.ControlStatus
import pt.alinha.core.compliance.Evaluation
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Most urgent first: the order of the entries is the order of the roadmap. */
enum class ActionPriority(val days: Long) { HIGH(30), MEDIUM(90), LOW(180) }

/** Who carries the action out, until the consultant reassigns it (assessment#8). */
enum class ActionOwner { RESPONSIBLE, TECHNICAL }

/** One remediation action, planned from one control the engine found [ControlStatus.PARTIAL] or a gap. */
data class PlannedAction(
    val controlId: String,
    val title: String,
    val priority: ActionPriority,
    val owner: ActionOwner,
    val dueOn: LocalDate,
    /** The legal references of every catalogue requirement the control satisfies, in catalogue order. */
    val legalReferences: List<String>,
    /** What proves the action is done: the control's verification criteria. */
    val evidence: List<String>,
)

/**
 * The deterministic baseline roadmap (assessment#6, FR13): one action per control that is not compliant. No LLM
 * involved (RNF05); the AI recommendations come on top of it (assessment#7) and the consultant validates the whole
 * (assessment#8). [VERSION] is stored with each roadmap, so a roadmap can always be explained.
 *
 * Not yet: raising the priority with the risk levels of Risk & Assets, whose public API has no risks yet.
 */
object RoadmapRules {
    const val VERSION = "roadmap-rules/1"

    /**
     * Gap severity x level: a basic control is needed for any compliance level (levels are cumulative,
     * Regulamento 756/2026 art. 30.º n.º 2), so its gaps come first; a gap outweighs a partial control.
     */
    fun priority(level: ComplianceLevel, status: ControlStatus): ActionPriority {
        val basic = level == ComplianceLevel.BASIC
        return when (status) {
            ControlStatus.GAP -> if (basic) ActionPriority.HIGH else ActionPriority.MEDIUM
            ControlStatus.PARTIAL -> if (basic) ActionPriority.MEDIUM else ActionPriority.LOW
            ControlStatus.COMPLIANT -> error("a compliant control needs no action")
        }
    }

    /** The actions for [evaluation] of [catalogue], most urgent first and then in catalogue order. */
    fun plan(catalogue: Catalogue, evaluation: Evaluation, evaluatedAt: Instant): List<PlannedAction> {
        val controls = catalogue.controls.associateBy { it.id }
        val evaluationDay = LocalDate.ofInstant(evaluatedAt, ZoneOffset.UTC)
        return evaluation.controls
            .filter { it.status != ControlStatus.COMPLIANT }
            .map { evaluated ->
                val control = controls.getValue(evaluated.controlId)
                val priority = priority(evaluated.level, evaluated.status)
                PlannedAction(
                    controlId = control.id,
                    title = control.title,
                    priority = priority,
                    owner = if (control.technical) ActionOwner.TECHNICAL else ActionOwner.RESPONSIBLE,
                    dueOn = evaluationDay.plusDays(priority.days),
                    legalReferences = catalogue.requirements.filter { control.id in it.controls }.map { it.reference },
                    evidence = control.evidence,
                )
            }
            // Stable sort: within one priority, the evaluation's (catalogue) order stays.
            .sortedBy { it.priority.ordinal }
    }
}
