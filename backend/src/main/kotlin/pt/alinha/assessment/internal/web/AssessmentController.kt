package pt.alinha.assessment.internal.web

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import pt.alinha.assessment.internal.AssessmentDetail
import pt.alinha.assessment.internal.AssessmentPage
import pt.alinha.assessment.internal.AssessmentService
import pt.alinha.assessment.internal.SavedAnswer
import pt.alinha.core.access.RequiresPermission
import pt.alinha.core.compliance.Answer
import java.util.UUID

/**
 * Body of `POST /assessments`. The scope comes from the organization plugin (core#25): a `scope` still sent by an old
 * client is deprecated and ignored (unknown properties are not read).
 */
data class StartAssessmentRequest(val framework: String)

/** Body of `PUT /assessments/{id}/answers/{questionId}`. */
data class SaveAnswerRequest(val answer: Answer)

@RestController
@RequestMapping("/api/assessment")
class AssessmentController(private val assessments: AssessmentService) {

    /** Walking skeleton: proves the plugin is wired end to end. Every role may call it (`plugins:read`). */
    @RequiresPermission("plugins")
    @GetMapping("/ping")
    fun ping() = mapOf("plugin" to "assessment", "status" to "ok")

    @RequiresPermission("assessment", "start")
    @PostMapping("/assessments")
    @ResponseStatus(HttpStatus.CREATED)
    fun start(@RequestBody body: StartAssessmentRequest): AssessmentDetail = assessments.start(body.framework)

    @RequiresPermission("assessment", "read")
    @GetMapping("/assessments")
    fun list(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
    ): AssessmentPage = assessments.list(page, pageSize)

    @RequiresPermission("assessment", "read")
    @GetMapping("/assessments/{assessmentId}")
    fun detail(@PathVariable("assessmentId") assessmentId: UUID): AssessmentDetail = assessments.detail(assessmentId)

    @RequiresPermission("assessment", "answer")
    @PutMapping("/assessments/{assessmentId}/answers/{questionId}")
    fun answer(
        @PathVariable("assessmentId") assessmentId: UUID,
        @PathVariable("questionId") questionId: String,
        @RequestBody body: SaveAnswerRequest,
    ): SavedAnswer = assessments.saveAnswer(assessmentId, questionId, body.answer)
}
