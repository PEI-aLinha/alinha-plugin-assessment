package pt.alinha.assessment.internal.domain

import pt.alinha.assessment.internal.FrameworkNotInScopeException
import pt.alinha.core.compliance.ApplicabilityResult
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.Applies
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.Nis2Classification

/** What an assessment is cut to, fixed when it starts. */
data class AssessmentScope(
    val framework: String,
    val maxLevel: ComplianceLevel,
    val nis2Classification: Nis2Classification,
    val rulesVersion: String,
)

object ScopeRules {
    /**
     * In scope when the rules say YES or REVIEW, or when they do not judge the framework at all (e.g. soc-cmm).
     * Only nis2-qnrcs is cut by the NIS2 classification; every other framework gets every level.
     */
    fun resolve(framework: String, applicability: ApplicabilityResult): AssessmentScope {
        val entry = applicability.frameworks.firstOrNull { it.framework == framework }
        if (entry?.applies == Applies.NO) throw FrameworkNotInScopeException(framework)

        val maxLevel =
            if (framework == ApplicabilityRules.NIS2) maxLevel(applicability.nis2Classification) else ComplianceLevel.HIGH
        return AssessmentScope(framework, maxLevel, applicability.nis2Classification, applicability.rulesVersion)
    }

    /**
     * Highest compliance level asked for each NIS2 classification.
     *
     * This table is an assumption that still has to be checked against Regulation 756/2026. The result is stored
     * in each assessment, so changing the table later never changes an existing assessment's questions (RNF05).
     */
    fun maxLevel(classification: Nis2Classification): ComplianceLevel = when (classification) {
        Nis2Classification.ESSENTIAL -> ComplianceLevel.HIGH
        Nis2Classification.IMPORTANT -> ComplianceLevel.SUBSTANTIAL
        Nis2Classification.TO_BE_ASSESSED -> ComplianceLevel.HIGH
        Nis2Classification.NOT_COVERED -> ComplianceLevel.BASIC
    }
}