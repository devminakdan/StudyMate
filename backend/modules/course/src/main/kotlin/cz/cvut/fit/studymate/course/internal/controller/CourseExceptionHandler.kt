package cz.cvut.fit.studymate.course.internal.controller

import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice


@RestControllerAdvice
internal class CourseExceptionHandler {
    @ExceptionHandler(CourseNotFoundException::class)
    fun handleCourseNotFound(ex: CourseNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message!!))

    @ExceptionHandler(CourseAccessDeniedException::class)
    fun handleCourseAccessDenied(ex: CourseAccessDeniedException): ResponseEntity<ErrorResponse> =
        ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ErrorResponse(ex.message!!))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors.joinToString(", ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message))
    }
}
