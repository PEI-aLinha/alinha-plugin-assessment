package pt.alinha.assessment.internal

import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.core.compliance.Answer
import java.util.UUID

/** The applicability rules say this framework does not apply to the organization. */
class FrameworkNotInScopeException(
    val framework: String,
) : RuntimeException("Framework $framework does not apply to this organization")

/** The organization has not answered the scope questions (organization plugin) yet: nothing to cut the questions to. */
class ScopeNotAnsweredException : RuntimeException("The organization has not answered the scope questions yet")

/** The framework id does not follow the catalogue folder pattern (e.g. `nis2-qnrcs`). */
class InvalidFrameworkException(
    val framework: String,
) : RuntimeException("Framework id is not valid: $framework")

/** No catalogue has been published for this framework yet. */
class CatalogueNotFoundException(
    val framework: String,
) : RuntimeException("No catalogue for framework $framework")

/** The organization already has an assessment of this framework that is not validated. */
class AssessmentAlreadyOpenException(
    val existingId: UUID,
) : RuntimeException("Assessment $existingId of this framework is still open")

/** No such assessment in the current organization (also for another organization's id). */
class AssessmentNotFoundException : RuntimeException("Assessment not found")

/** The question exists in some catalogue but not in this assessment (above its level, or another framework). */
class QuestionNotInAssessmentException(
    val questionId: String,
) : RuntimeException("Question $questionId is not part of this assessment")

/** A valid answer value that this question does not offer (e.g. `unknown` on a yes/no question). */
class AnswerNotOfferedException(
    val questionId: String,
    val answer: Answer,
) : RuntimeException("Question $questionId does not offer $answer")

/** Answers change only while the assessment is a draft. */
class AssessmentNotEditableException(
    val status: AssessmentStatus,
) : RuntimeException("Assessment is $status: answers can no longer change")

/** A `page` or `pageSize` query parameter outside the contract's range (page >= 0, pageSize 1..100). */
class InvalidPageException(
    val field: String,
) : RuntimeException("Query parameter $field is out of range")
