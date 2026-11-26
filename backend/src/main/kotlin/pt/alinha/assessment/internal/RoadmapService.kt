package pt.alinha.assessment.internal

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import pt.alinha.assessment.internal.domain.RoadmapRules
import pt.alinha.assessment.internal.persistence.RoadmapActionEntity
import pt.alinha.assessment.internal.persistence.RoadmapActionRepository
import pt.alinha.assessment.internal.persistence.RoadmapEntity
import pt.alinha.assessment.internal.persistence.RoadmapRepository
import pt.alinha.core.audit.AuditTrail
import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.Evaluation
import tools.jackson.databind.json.JsonMapper
import java.time.Instant
import java.util.UUID

/** The remediation roadmap of an assessment (assessment#6). The tenant comes from the entities (TenantOwnedEntity). */
@Service
class RoadmapService(
    private val roadmaps: RoadmapRepository,
    private val actions: RoadmapActionRepository,
    private val audit: AuditTrail,
    private val json: JsonMapper,
) {
    /**
     * Plans and stores the roadmap for [evaluation], audited (RNF04). Runs inside the submit's transaction, so the
     * roadmap exists exactly when the results do.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    fun plan(assessmentId: UUID, catalogue: Catalogue, evaluation: Evaluation, at: Instant): RoadmapEntity {
        val roadmap = roadmaps.save(RoadmapEntity(UUID.randomUUID(), assessmentId, RoadmapRules.VERSION, at))
        val planned = RoadmapRules.plan(catalogue, evaluation, at)
        actions.saveAll(
            planned.mapIndexed { position, action ->
                RoadmapActionEntity(
                    id = UUID.randomUUID(),
                    roadmapId = roadmap.id,
                    position = position,
                    controlId = action.controlId,
                    title = action.title,
                    priority = action.priority,
                    owner = action.owner,
                    dueOn = action.dueOn,
                    legalReferences = json.writeValueAsString(action.legalReferences),
                    evidence = json.writeValueAsString(action.evidence),
                )
            },
        )
        audit.record(
            "roadmap.generate", "roadmap", roadmap.id.toString(),
            mapOf("assessmentId" to assessmentId.toString(), "rulesVersion" to RoadmapRules.VERSION, "actions" to planned.size),
        )
        return roadmap
    }
}
