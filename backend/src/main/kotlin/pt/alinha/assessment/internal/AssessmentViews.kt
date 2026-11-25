package pt.alinha.assessment.internal

import pt.alinha.assessment.internal.domain.AssessmentStatus
import pt.alinha.core.compliance.Answer
import pt.alinha.core.compliance.Level
import pt.alinha.core.compliance.Nis2Classification
import java.time.Instant
import java.util.UUID

// Response bodies. Field names follow the OpenAPI schemas in contracts/openapi/alinha.yaml (assessment tag).

/** The contract's `Level`: only what the screen shows, not the rest of the catalogue's level (e.g. evidence bars). */
data class LevelView(val id: String, val label: String) {
    constructor(level: Level) : this(level.id, level.label)
}

data class ProgressView(val answered: Int, val total: Int)

data class QuestionView(
    val id: String,
    val text: String,
    val explanation: String,
    val example: String,
    val options: List<Answer>,
    val answer: Answer?,
    val answeredAt: Instant?,
)

data class ControlView(
    val id: String,
    val title: String,
    /** Deprecated in the contract: the level id. [scaleLevel] has the label too (ADR 0007). */
    val level: String,
    val scaleLevel: LevelView,
    val technical: Boolean,
    val questions: List<QuestionView>,
)

data class AssessmentDetail(
    val id: UUID,
    val framework: String,
    val catalogueVersion: Int,
    val catalogueTitle: String,
    val status: String,
    val nis2Classification: String,
    val nis2ClassificationOfficial: Boolean,
    /** Deprecated in the contract: the level id. [maxScaleLevel] has the label too (ADR 0007). */
    val maxLevel: String,
    val maxScaleLevel: LevelView,
    val rulesVersion: String,
    val progress: ProgressView,
    val createdAt: Instant,
    val updatedAt: Instant,
    val controls: List<ControlView>,
)

data class AssessmentSummary(
    val id: UUID,
    val framework: String,
    val catalogueVersion: Int,
    val status: String,
    val progress: ProgressView,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class AssessmentPage(val items: List<AssessmentSummary>, val page: Int, val pageSize: Int, val total: Long)

data class SavedAnswer(val questionId: String, val answer: Answer, val answeredAt: Instant, val progress: ProgressView)

/** `draft`, `submitted`, ... as in the contract's AssessmentStatus. */
internal fun AssessmentStatus.apiValue(): String = name.lowercase()

/** `essential`, `not-covered`, ... as in the contract's nis2Classification. */
internal fun Nis2Classification.apiValue(): String = name.lowercase().replace('_', '-')
