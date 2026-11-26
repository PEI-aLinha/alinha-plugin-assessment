package pt.alinha.assessment.internal.domain

import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.Control
import pt.alinha.core.compliance.Level
import pt.alinha.core.compliance.Question

/**
 * The questions of one assessment: the catalogue's controls up to [maxLevel] (a level id of the catalogue's own scale,
 * compared by position), in catalogue order.
 */
class Questionnaire(
    val catalogue: Catalogue,
    val maxLevel: String,
) {
    private val maxIndex = catalogue.levelIndex(maxLevel).also {
        require(it >= 0) { "Level '$maxLevel' is not in the scale of ${catalogue.framework} v${catalogue.version}" }
    }

    val controls: List<Control> = catalogue.controls.filter { catalogue.levelIndex(it.level) <= maxIndex }

    /** The [maxLevel] with its label. */
    val maxScaleLevel: Level = catalogue.levels[maxIndex]

    private val questionsById: Map<String, Question> = controls.flatMap { it.questions }.associateBy { it.id }

    val questionCount: Int = controls.sumOf { it.questions.size }

    /** Null when the question is not part of this questionnaire (above [maxLevel], or not in the catalogue). */
    fun question(id: String): Question? = questionsById[id]
}