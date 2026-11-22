package pt.alinha.assessment.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import pt.alinha.assessment.internal.domain.AssessmentStatus.DRAFT
import pt.alinha.assessment.internal.domain.AssessmentStatus.EVALUATED
import pt.alinha.assessment.internal.domain.AssessmentStatus.SUBMITTED
import pt.alinha.assessment.internal.domain.AssessmentStatus.VALIDATED

class AssessmentStatusTest {
    @ParameterizedTest
    @CsvSource("DRAFT,SUBMITTED", "SUBMITTED,EVALUATED", "EVALUATED,VALIDATED")
    fun `moves only to the next state`(from: AssessmentStatus, to: AssessmentStatus) {
        assertThat(from.canMoveTo(to)).isTrue()
    }

    @Test
    fun `every other transition is refused`() {
        val allowed = setOf(DRAFT to SUBMITTED, SUBMITTED to EVALUATED, EVALUATED to VALIDATED)
        for (from in AssessmentStatus.entries) {
            for (to in AssessmentStatus.entries) {
                if (from to to !in allowed) assertThat(from.canMoveTo(to)).describedAs("$from -> $to").isFalse()
            }
        }
    }

    @Test
    fun `only a draft accepts answers`() {
        assertThat(AssessmentStatus.entries.filter { it.acceptsAnswers }).containsExactly(DRAFT)
    }
}
