package pt.alinha.assessment.internal.domain

/**
 * Lifecycle of an assessment: draft -> submitted -> evaluated -> validated, one step at a time.
 *
 * Answers are frozen once the assessment is submitted. Submitting and evaluating come in assessment#5, validating
 * in assessment#8. [canMoveTo] relies on the declaration order: do not reorder the entries.
 */
enum class AssessmentStatus {
    DRAFT,
    SUBMITTED,
    EVALUATED,
    VALIDATED,
    ;

    /** True only for the next state in the lifecycle. */
    fun canMoveTo(target: AssessmentStatus): Boolean = target.ordinal == ordinal + 1

    /** Only a draft takes new or changed answers. */
    val acceptsAnswers: Boolean
        get() = this == DRAFT
}

class IllegalStatusTransitionException(
    val from: AssessmentStatus,
    val to: AssessmentStatus,
) : RuntimeException("Assessment cannot move from $from to $to")
