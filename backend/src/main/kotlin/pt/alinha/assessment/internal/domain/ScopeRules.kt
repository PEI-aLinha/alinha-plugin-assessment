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
     * Only nis2-qnrcs is cut by the NIS2 classification (the official one when registered, else the indicative one);
     * every other framework gets every level.
     */
    fun resolve(framework: String, applicability: ApplicabilityResult): AssessmentScope {
        val entry = applicability.frameworks.firstOrNull { it.framework == framework }
        // The CNCS only qualifies covered entities: an official qualification puts NIS2 in scope.
        val officiallyCovered = framework == ApplicabilityRules.NIS2 && applicability.officialNis2Classification != null
        if (entry?.applies == Applies.NO && !officiallyCovered) throw FrameworkNotInScopeException(framework)

        val classification = applicability.effectiveNis2Classification
        val maxLevel = if (framework == ApplicabilityRules.NIS2) maxLevel(classification) else ComplianceLevel.HIGH
        return AssessmentScope(framework, maxLevel, classification, applicability.rulesVersion)
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