package pt.alinha.assessment.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.tenant.TenantOwnedEntity
import java.time.Instant
import java.util.UUID

/** What the Compliance Engine computed for one assessment on submit. Never updated (RNF05). */
@Entity
@Table(schema = "assessment", name = "result")
class ResultEntity(
    @Id val id: UUID,
    @Column(name = "assessment_id", updatable = false) val assessmentId: UUID,
    @Column(name = "engine_version", updatable = false) val engineVersion: String,
    @Enumerated(EnumType.STRING) @Column(name = "current_level", updatable = false) val currentLevel: ComplianceLevel?,
    @Enumerated(EnumType.STRING) @Column(name = "target_level", updatable = false) val targetLevel: ComplianceLevel,
    @Column(updatable = false) val coverage: Double,
    /** The [StoredControl]s as JSON, in catalogue order. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(updatable = false) val controls: String,
    @Column(name = "evaluated_at", updatable = false) val evaluatedAt: Instant,
) : TenantOwnedEntity()

/** One control in [ResultEntity.controls]; enum names, so the stored JSON does not depend on the API's spelling. */
data class StoredControl(val controlId: String, val level: String, val status: String)
