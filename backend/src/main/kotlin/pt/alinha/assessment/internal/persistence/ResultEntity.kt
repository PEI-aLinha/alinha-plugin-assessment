package pt.alinha.assessment.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.tenant.TenantOwnedEntity
import java.time.Instant
import java.util.UUID

/**
 * What the Compliance Engine computed for one assessment on submit. Never updated (RNF05). Levels are level ids of
 * the catalogue's scale (ADR 0007, see [levelId]), not enum names, so other scales need no migration.
 */
@Entity
@Table(schema = "assessment", name = "result")
class ResultEntity(
    @Id val id: UUID,
    @Column(name = "assessment_id", updatable = false) val assessmentId: UUID,
    @Column(name = "engine_version", updatable = false) val engineVersion: String,
    @Column(name = "current_level", updatable = false) val currentLevel: String?,
    @Column(name = "target_level", updatable = false) val targetLevel: String,
    @Column(updatable = false) val coverage: Double,
    /** The [StoredControl]s as JSON, in catalogue order. */
    @JdbcTypeCode(SqlTypes.JSON) @Column(updatable = false) val controls: String,
    @Column(name = "evaluated_at", updatable = false) val evaluatedAt: Instant,
) : TenantOwnedEntity()

/** One control in [ResultEntity.controls]: a level id and the status enum name. */
data class StoredControl(val controlId: String, val level: String, val status: String)

/** The level id of a nis2-qnrcs level (`basic`, `substantial`, `high`), as the level-scale catalogues spell it. */
val ComplianceLevel.levelId: String get() = name.lowercase()

/** The level of a stored level id. Only the nis2-qnrcs scale for now; other scales come with the level-scale work. */
fun complianceLevel(levelId: String): ComplianceLevel = ComplianceLevel.valueOf(levelId.uppercase())
