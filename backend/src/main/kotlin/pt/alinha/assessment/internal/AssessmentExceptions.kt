package pt.alinha.assessment.internal

import java.util.UUID

/** The applicability rules say this framework does not apply to the organization. */
class FrameworkNotInScopeException(
    val framework: String,
) : RuntimeException("Framework $framework does not apply to this organization")

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
