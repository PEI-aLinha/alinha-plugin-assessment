package pt.alinha.assessment.internal.domain

import pt.alinha.core.compliance.Catalogue
import pt.alinha.core.compliance.ComplianceLevel
import pt.alinha.core.compliance.Control
import pt.alinha.core.compliance.Question

/** The questions of one assessment: the catalogue's controls up to [maxLevel], in catalogue order. */
class Questionnaire(
    val catalogue: Catalogue,
    val maxLevel: ComplianceLevel,
) {
    val controls: List<Control> = catalogue.controls.filter { it.level.ordinal <= maxLevel.ordinal }

    private val questionsById: Map<String, Question> = controls.flatMap { it.questions }.associateBy { it.id }

    val questionCount: Int = controls.sumOf { it.questions.size }

    /** Null when the question is not part of this questionnaire (above [maxLevel], or not in the catalogue). */
    fun question(id: String): Question? = questionsById[id]
}