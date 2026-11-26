package pt.alinha.assessment.internal.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import pt.alinha.core.compliance.Answer
import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.Control
import pt.alinha.core.compliance.Level
import pt.alinha.core.compliance.Question

class QuestionnaireTest {
    private fun control(id: String, level: String, questions: Int) = Control(
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
            control("A", "basic", 1),
            control("B", "substantial", 2),
            control("C", "high", 1),
        ),
    )

    @Test
    fun `keeps controls up to the ceiling in catalogue order`() {
        val q = Questionnaire(catalogue, "substantial")
        assertThat(q.controls.map { it.id }).containsExactly("A", "B")
        assertThat(q.questionCount).isEqualTo(3)
    }

    @Test
    fun `high includes everything`() {
        val q = Questionnaire(catalogue, "high")
        assertThat(q.controls.map { it.id }).containsExactly("A", "B", "C")
        assertThat(q.questionCount).isEqualTo(4)
    }

    @Test
    fun `finds only questions inside the ceiling`() {
        val q = Questionnaire(catalogue, "basic")
        assertThat(q.question("A-Q1")).isNotNull()
        assertThat(q.question("C-Q1")).isNull()
        assertThat(q.question("NOPE")).isNull()
    }

    @Test
    fun `cuts by position in the catalogue's own level scale`() {
        val maturity = catalogue.copy(
            framework = "soc-cmm",
            levels = listOf(Level("1", "Inicial"), Level("2", "Gerido"), Level("3", "Definido")),
            controls = listOf(control("X", "1", 1), control("Y", "3", 1), control("Z", "2", 1)),
        )
        assertThat(Questionnaire(maturity, "2").controls.map { it.id }).containsExactly("X", "Z")
    }
}
