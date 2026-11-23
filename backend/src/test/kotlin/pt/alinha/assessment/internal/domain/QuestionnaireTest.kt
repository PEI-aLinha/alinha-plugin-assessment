package pt.alinha.assessment.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pt.alinha.core.compliance.Answer
import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.Control
import pt.alinha.core.compliance.Question

class QuestionnaireTest {
    private fun control(id: String, level: ComplianceLevel, questions: Int) = Control(
        id = id,
        title = "Control $id",
        level = level,
        technical = false,
        evidence = listOf("Evidence $id"),
        questions = (1..questions).map {
            Question("$id-Q$it", "Question $id-$it", "Why", "For example", listOf(Answer.YES, Answer.NO))
        },
    )

    private val catalogue = Catalogue(
        framework = "nis2-qnrcs",
        version = 1,
        title = "Test catalogue",
        sources = listOf("Test source"),
        requirements = emptyList(),
        controls = listOf(
            control("A", ComplianceLevel.BASIC, 1),
            control("B", ComplianceLevel.SUBSTANTIAL, 2),
            control("C", ComplianceLevel.HIGH, 1),
        ),
    )

    @Test
    fun `keeps controls up to the ceiling in catalogue order`() {
        val q = Questionnaire(catalogue, ComplianceLevel.SUBSTANTIAL)
        assertThat(q.controls.map { it.id }).containsExactly("A", "B")
        assertThat(q.questionCount).isEqualTo(3)
    }

    @Test
    fun `high includes everything`() {
        val q = Questionnaire(catalogue, ComplianceLevel.HIGH)
        assertThat(q.controls.map { it.id }).containsExactly("A", "B", "C")
        assertThat(q.questionCount).isEqualTo(4)
    }

    @Test
    fun `finds only questions inside the ceiling`() {
        val q = Questionnaire(catalogue, ComplianceLevel.BASIC)
        assertThat(q.question("A-Q1")).isNotNull()
        assertThat(q.question("C-Q1")).isNull()
        assertThat(q.question("NOPE")).isNull()
    }
}
