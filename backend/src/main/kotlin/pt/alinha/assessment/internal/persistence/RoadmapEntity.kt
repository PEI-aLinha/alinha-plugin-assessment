package pt.alinha.assessment.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import pt.alinha.assessment.internal.domain.ActionOwner
import pt.alinha.assessment.internal.domain.ActionPriority
import pt.alinha.core.tenant.TenantOwnedEntity
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** The roadmap planned for one assessment on submit (assessment#6), with the rules version that planned it. */
@Entity
@Table(schema = "assessment", name = "roadmap")
class RoadmapEntity(
    @Id val id: UUID,
    @Column(name = "assessment_id", updatable = false) val assessmentId: UUID,
    @Column(name = "rules_version", updatable = false) val rulesVersion: String,
    @Column(name = "generated_at", updatable = false) val generatedAt: Instant,
) : TenantOwnedEntity()

/** One action of a [RoadmapEntity]. */
@Entity
@Table(schema = "assessment", name = "roadmap_action")
class RoadmapActionEntity(
    @Id val id: UUID,
    @Column(name = "roadmap_id", updatable = false) val roadmapId: UUID,
    @Column(updatable = false) val position: Int,
    @Column(name = "control_id", updatable = false) val controlId: String,
    @Column(updatable = false) val title: String,
    @Enumerated(EnumType.STRING) val priority: ActionPriority,
    @Enumerated(EnumType.STRING) val owner: ActionOwner,
    @Column(name = "due_on") val dueOn: LocalDate,
    /** JSON array of strings. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "legal_references", updatable = false) val legalReferences: String,
    /** JSON array of strings. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(updatable = false) val evidence: String,
) : TenantOwnedEntity()
