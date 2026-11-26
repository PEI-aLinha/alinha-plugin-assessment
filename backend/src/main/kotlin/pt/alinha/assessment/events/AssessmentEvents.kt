package pt.alinha.assessment.events

import pt.alinha.pluginapi.DomainEvent
import pt.alinha.pluginapi.TenantId
import java.time.Instant
import java.util.UUID

// Events published by this plugin (listed in plugin.yaml > publishes). They carry ids only.
//
// `runAs` must start outside a transaction, so listeners use `@Async @TransactionalEventListener` with no
// `@Transactional`, and open the transaction inside `tenants.runAs(event) { ... }` (docs/events.md in the main repo).

/** The organization submitted the assessment: its answers are frozen (assessment#5, FR10). */
data class AssessmentSubmitted(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val assessmentId: UUID,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent

/**
 * The Compliance Engine computed the assessment's results; they wait for the consultant (assessment#8). The
 * framework and catalogue version let a listener (dashboard-reports) group results without reading them back.
 */
data class AssessmentEvaluated(
    override val tenantId: TenantId,
    override val correlationId: UUID,
    val assessmentId: UUID,
    val framework: String,
    val catalogueVersion: Int,
    override val occurredAt: Instant = Instant.now(),
) : DomainEvent
