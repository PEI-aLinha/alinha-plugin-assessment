package pt.alinha.assessment.internal

/** The applicability rules say this framework does not apply to the organization. */
class FrameworkNotInScopeException(
    val framework: String,
) : RuntimeException("Framework $framework does not apply to this organization")