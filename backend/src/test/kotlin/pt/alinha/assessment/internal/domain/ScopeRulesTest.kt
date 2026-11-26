package pt.alinha.assessment.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import pt.alinha.assessment.internal.FrameworkNotInScopeException
import pt.alinha.core.compliance.ApplicabilityResult
import pt.alinha.core.compliance.ApplicabilityRules
import pt.alinha.core.compliance.Applies
import pt.alinha.core.compliance.CompanySize
import pt.alinha.core.compliance.FrameworkApplicability
import pt.alinha.core.compliance.Nis2Classification

class ScopeRulesTest {
    private fun result(
        classification: Nis2Classification,
        vararg applies: Pair<String, Applies>,
        official: Nis2Classification? = null,
    ) = ApplicabilityResult(
        rulesVersion = "r1",
        size = CompanySize.MEDIUM,
        nis2Classification = classification,
        cncsMayQualify = false,
        frameworks = applies.map { (framework, a) -> FrameworkApplicability(framework, a, emptyList()) },
        officialNis2Classification = official,
    )

    @ParameterizedTest
    @CsvSource("ESSENTIAL,high", "IMPORTANT,substantial", "TO_BE_ASSESSED,high", "NOT_COVERED,basic")
    fun `nis2 classification sets the highest level asked`(c: Nis2Classification, level: String) {
        assertThat(ScopeRules.maxLevel(c)).isEqualTo(level)
    }

    @Test
    fun `nis2 in scope uses the classification ceiling and keeps the rules version`() {
        val scope = ScopeRules.resolve(
            ApplicabilityRules.NIS2,
            result(Nis2Classification.IMPORTANT, ApplicabilityRules.NIS2 to Applies.YES),
        )
        assertThat(scope).isEqualTo(
            AssessmentScope("nis2-qnrcs", "substantial", Nis2Classification.IMPORTANT, "r1"),
        )
    }

    @Test
    fun `review counts as in scope`() {
        val scope = ScopeRules.resolve(
            ApplicabilityRules.NIS2,
            result(Nis2Classification.NOT_COVERED, ApplicabilityRules.NIS2 to Applies.REVIEW),
        )
        assertThat(scope.maxLevel).isEqualTo("basic")
    }

    @Test
    fun `a framework that does not apply is refused`() {
        assertThatThrownBy {
            ScopeRules.resolve(
                ApplicabilityRules.GDPR,
                result(Nis2Classification.IMPORTANT, ApplicabilityRules.GDPR to Applies.NO),
            )
        }.isInstanceOfSatisfying(FrameworkNotInScopeException::class.java) {
            assertThat(it.framework).isEqualTo("gdpr")
        }
    }

    @Test
    fun `other frameworks get every level of their scale`() {
        val scope = ScopeRules.resolve(
            ApplicabilityRules.GDPR,
            result(Nis2Classification.IMPORTANT, ApplicabilityRules.GDPR to Applies.YES),
        )
        assertThat(scope.maxLevel).isNull()
    }

    @Test
    fun `a framework the rules do not judge is in scope`() {
        val scope = ScopeRules.resolve("soc-cmm", result(Nis2Classification.IMPORTANT))
        assertThat(scope.maxLevel).isNull()
    }

    @Test
    fun `the official CNCS qualification wins over the indicative classification`() {
        val scope = ScopeRules.resolve(
            ApplicabilityRules.NIS2,
            result(Nis2Classification.IMPORTANT, ApplicabilityRules.NIS2 to Applies.YES, official = Nis2Classification.ESSENTIAL),
        )
        assertThat(scope.maxLevel).isEqualTo("high")
        assertThat(scope.nis2Classification).isEqualTo(Nis2Classification.ESSENTIAL)
    }

    @Test
    fun `an official qualification puts nis2 in scope even when the rules say it does not apply`() {
        val scope = ScopeRules.resolve(
            ApplicabilityRules.NIS2,
            result(Nis2Classification.NOT_COVERED, ApplicabilityRules.NIS2 to Applies.NO, official = Nis2Classification.IMPORTANT),
        )
        assertThat(scope.maxLevel).isEqualTo("substantial")
    }
}
