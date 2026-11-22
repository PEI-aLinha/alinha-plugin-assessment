package pt.alinha.assessment.internal.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.Table
import pt.alinha.core.compliance.Answer
import pt.alinha.core.tenant.TenantOwnedEntity
import java.time.Instant
import java.util.UUID

/** The current answer to one question; earlier values are in the audit trail. */
@Entity
@Table(schema = "assessment", name = "answer")
class AnswerEntity(
    @Id val id: UUID,
    @Column(name = "assessment_id", updatable = false) val assessmentId: UUID,
    @Column(name = "question_id", updatable = false) val questionId: String,
    value: Answer,
    answeredAt: Instant,
) : TenantOwnedEntity() {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var value: Answer = value
        protected set

    @Column(name = "answered_at", nullable = false)
    var answeredAt: Instant = answeredAt
        protected set

    /** Returns the previous value, or null when [newValue] is the same (then nothing changes, not even [answeredAt]). */
    fun change(newValue: Answer, now: Instant): Answer? {
        if (newValue == value) return null
        val previous = value
        value = newValue
        answeredAt = now
        return previous
    }
}
