package cz.cvut.fit.studymate.retrieval.internal.controller

import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.retrieval.internal.exceptions.InvalidRetrievalRequestException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalAccessDeniedException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalCourseNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalMaterialNotFoundException
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice(assignableTypes = [RetrievalController::class])
@Order(Ordered.HIGHEST_PRECEDENCE)
internal class RetrievalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }

    @ExceptionHandler(InvalidRetrievalRequestException::class)
    fun handleInvalidRequest(ex: InvalidRetrievalRequestException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Invalid retrieval request"))

    @ExceptionHandler(RetrievalCourseNotFoundException::class)
    fun handleCourseNotFound(ex: RetrievalCourseNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Course not found"))

    @ExceptionHandler(RetrievalMaterialNotFoundException::class)
    fun handleMaterialNotFound(ex: RetrievalMaterialNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Material not found"))

    @ExceptionHandler(RetrievalAccessDeniedException::class)
    fun handleAccessDenied(ex: RetrievalAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ex.message ?: "Access to course is denied"))

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("Invalid JSON body: provide a string query and an optional integer limit"))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleInvalidParameter(ex: MethodArgumentTypeMismatchException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse("Invalid value for parameter '${ex.name}'"))

    @ExceptionHandler(EmbeddingProviderException::class)
    fun handleEmbeddingFailure(): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.BAD_GATEWAY)
            .body(ErrorResponse("Embedding generation failed"))
}
