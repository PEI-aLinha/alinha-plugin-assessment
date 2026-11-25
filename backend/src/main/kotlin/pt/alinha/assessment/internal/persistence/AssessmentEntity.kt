package pt.alinha.assessment.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.assessment.internal.domain.IllegalStatusTransitionException
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.Nis2Classification
import pt.alinha.core.tenant.TenantOwnedEntity
import java.time.Instant
import java.util.UUID

/** One assessment of one framework. What it was started with never changes (RNF05, RNF18). */
@Entity
@Table(schema = "assessment", name = "assessment")
class AssessmentEntity(
    @Id val id: UUID,
    @Column(updatable = false) val framework: String,
    @Column(name = "catalogue_version", updatable = false) val catalogueVersion: Int,
    @Enumerated(EnumType.STRING) @Column(name = "max_level", updatable = false) val maxLevel: ComplianceLevel,
    @Enumerated(EnumType.STRING) @Column(name = "nis2_classification", updatable = false)
    val nis2Classification: Nis2Classification,
    @Column(name = "rules_version", updatable = false) val rulesVersion: String,
    /**
     * The organization's scope on start, as JSON: an ApplicabilityInput before core#25, the ApplicabilityResult read from
     * the organization plugin since (self-declared answers; checked by the consultant).
     */
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "scope_input", updatable = false) val scopeInput: String,
    @Column(name = "created_at", updatable = false) val createdAt: Instant,
    /** [nis2Classification] is the official CNCS qualification, not the indicative one. */
    @Column(name = "nis2_classification_official", updatable = false) val nis2ClassificationOfficial: Boolean = false,
) : TenantOwnedEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AssessmentStatus = AssessmentStatus.DRAFT
        protected set

    /** Last real change, answers included, so the list can show where work stopped. */
    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = createdAt
        protected set

    fun moveTo(target: AssessmentStatus, now: Instant) {
        if (!status.canMoveTo(target)) throw IllegalStatusTransitionException(status, target)
        status = target
        updatedAt = now
    }

    fun touch(now: Instant) {
        updatedAt = now
    }
}
