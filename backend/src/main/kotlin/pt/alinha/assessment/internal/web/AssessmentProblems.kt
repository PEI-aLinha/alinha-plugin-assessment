package pt.alinha.assessment.internal.web

import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import pt.alinha.assessment.internal.AnswerNotOfferedException
import pt.alinha.assessment.internal.AssessmentAlreadyOpenException
import pt.alinha.assessment.internal.AssessmentIncompleteException
import pt.alinha.assessment.internal.ResultsNotAvailableException
import pt.alinha.assessment.internal.AssessmentNotEditableException
import pt.alinha.assessment.internal.AssessmentNotFoundException
import pt.alinha.assessment.internal.CatalogueNotFoundException
import pt.alinha.assessment.internal.FrameworkNotInScopeException
import pt.alinha.assessment.internal.InvalidFrameworkException
import pt.alinha.assessment.internal.InvalidPageException
import pt.alinha.assessment.internal.QuestionNotInAssessmentException
import tools.jackson.core.JacksonException

private const val VALIDATION_FAILED = "VALIDATION_FAILED"
private const val FIX_DATA = "Há dados por corrigir."
private const val INVALID_VALUE = "Este valor não é válido."
private const val UNREADABLE_BODY = "Não foi possível ler os dados enviados."
private const val MAX_CAUSE_DEPTH = 20

/** RFC 9457 Problem Details with `code` and `errors` (ADR 0003). `detail` is shown to the user, in Portuguese. */
@RestControllerAdvice(basePackageClasses = [AssessmentController::class])
class AssessmentProblems {

    /**
     * A body Jackson cannot read: malformed JSON, an unknown enum value, a missing field, or contradictory scope
     * answers (the core's `require(...)` checks run while the body is read). Names the field when Jackson knows it.
     */
    @ExceptionHandler
    fun unreadable(e: HttpMessageNotReadableException): ProblemDetail {
        val field = unreadableField(e)
        return problem(HttpStatus.UNPROCESSABLE_CONTENT, VALIDATION_FAILED, if (field == null) UNREADABLE_BODY else FIX_DATA)
            .apply { setProperty("errors", listOfNotNull(field?.let { error(it, INVALID_VALUE) })) }
    }

    @ExceptionHandler
    fun invalidFramework(e: InvalidFrameworkException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, VALIDATION_FAILED, FIX_DATA)
            .apply { setProperty("errors", listOf(error("framework", "Este regulamento não é válido."))) }

    /** The contract declares `400` for the list's query parameters. */
    @ExceptionHandler
    fun invalidPage(e: InvalidPageException) =
        problem(HttpStatus.BAD_REQUEST, VALIDATION_FAILED, FIX_DATA)
            .apply { setProperty("errors", listOf(error(e.field, INVALID_VALUE))) }

    @ExceptionHandler
    fun notInScope(e: FrameworkNotInScopeException) = problem(
        HttpStatus.UNPROCESSABLE_CONTENT, "FRAMEWORK_NOT_IN_SCOPE",
        "Pelas respostas dadas, este regulamento não se aplica à sua organização.",
    )

    @ExceptionHandler
    fun noCatalogue(e: CatalogueNotFoundException) = problem(
        HttpStatus.UNPROCESSABLE_CONTENT, "CATALOGUE_NOT_FOUND", "Ainda não há questionário para este regulamento.",
    )

    @ExceptionHandler
    fun alreadyOpen(e: AssessmentAlreadyOpenException) = problem(
        HttpStatus.CONFLICT, "ASSESSMENT_ALREADY_OPEN",
        "Já tem uma avaliação deste regulamento em curso. Continue essa avaliação.",
    ).apply { setProperty("assessmentId", e.existingId) }

    @ExceptionHandler
    fun notFound(e: AssessmentNotFoundException) =
        problem(HttpStatus.NOT_FOUND, "ASSESSMENT_NOT_FOUND", "Esta avaliação não existe.")

    @ExceptionHandler
    fun questionNotInAssessment(e: QuestionNotInAssessmentException) =
        problem(HttpStatus.NOT_FOUND, "QUESTION_NOT_IN_ASSESSMENT", "Esta pergunta não faz parte desta avaliação.")

    @ExceptionHandler
    fun answerNotOffered(e: AnswerNotOfferedException) =
        problem(HttpStatus.UNPROCESSABLE_CONTENT, VALIDATION_FAILED, FIX_DATA).apply {
            setProperty("errors", listOf(error("answer", "Esta resposta não é uma das opções desta pergunta.")))
        }

    @ExceptionHandler
    fun notEditable(e: AssessmentNotEditableException) = problem(
        HttpStatus.CONFLICT, "ASSESSMENT_NOT_EDITABLE",
        "Esta avaliação já foi entregue e as respostas já não podem ser alteradas.",
    )

    @ExceptionHandler
    fun incomplete(e: AssessmentIncompleteException) = problem(
        HttpStatus.UNPROCESSABLE_CONTENT, "ASSESSMENT_INCOMPLETE",
        "Ainda há perguntas por responder. Responda a todas antes de entregar a avaliação.",
    ).apply { setProperty("unanswered", e.unanswered) }

    @ExceptionHandler
    fun resultsNotAvailable(e: ResultsNotAvailableException) = problem(
        HttpStatus.CONFLICT, "RESULTS_NOT_AVAILABLE", "Esta avaliação ainda não tem resultados.",
    )

    private fun problem(status: HttpStatus, code: String, detail: String) =
        ProblemDetail.forStatusAndDetail(status, detail).apply { setProperty("code", code) }

    private fun error(field: String, message: String) = mapOf("field" to field, "message" to message)
}

/** The body's field Jackson failed on (`scope.gdpr`), or null if unknown. Same as the organization plugin's. */
private fun unreadableField(e: Throwable): String? =
    generateSequence(e) { it.cause }.take(MAX_CAUSE_DEPTH)
        .filterIsInstance<JacksonException>().firstOrNull()
        ?.path?.mapNotNull { it.propertyName }?.joinToString(".")?.ifEmpty { null }
