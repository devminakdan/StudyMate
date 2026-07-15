package cz.cvut.fit.studymate.course.internal.controller

import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.course.internal.dto.CourseResponse
import cz.cvut.fit.studymate.course.internal.dto.CreateCourseRequest
import cz.cvut.fit.studymate.course.internal.dto.UpdateCourseRequest
import cz.cvut.fit.studymate.course.internal.dto.toResponse
import cz.cvut.fit.studymate.course.internal.service.CourseService
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Courses", description = "CRUD for a user's own courses")
@RestController
@RequestMapping("/api/v1/courses")
internal class CourseController(
    private val courseService: CourseService
) {
    @Operation(
        summary = "Create a course",
        description = "Creates a new course owned by the current user.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "Course created", content = [Content(schema = Schema(implementation = CourseResponse::class))]),
        ApiResponse(responseCode = "400", description = "Validation failed: blank/too long name, or too long code", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or it's missing/invalid/unparseable", content = [Content()]),
        ApiResponse(responseCode = "409", description = "Caller already has a course with this name", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCourse(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateCourseRequest,
    ): CourseResponse =
        courseService.createCourse(user.id, request.name, request.code, request.description).toResponse()

    @Operation(
        summary = "List the current user's courses",
        description = "Paginated list of courses owned by the current user, newest first. Total count is returned in the X-Total-Count header.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Page of courses", content = [Content(array = ArraySchema(schema = Schema(implementation = CourseResponse::class)))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or it's missing/invalid/unparseable", content = [Content()]),
    )
    @GetMapping
    fun listCourses(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<CourseResponse>> {
        val courses = courseService.listCourses(user.id, page.coerceAtLeast(0), size.coerceIn(1, 100))
        val total = courseService.countCourses(user.id)
        return ResponseEntity.ok()
            .header("X-Total-Count", total.toString())
            .body(courses.map { it.toResponse() })
    }

    @Operation(
        summary = "Get a course by id",
        description = "Returns a single course. Only the owner can access it.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Course found", content = [Content(schema = Schema(implementation = CourseResponse::class))]),
        ApiResponse(responseCode = "403", description = "Authenticated but not the owner of this course", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "No course exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @GetMapping("/{id}")
    fun getCourse(
        @Parameter(description = "Course id") @PathVariable id: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): CourseResponse =
        courseService.getCourse(id, user.id).toResponse()

    @Operation(
        summary = "Update a course",
        description = "Replaces name/code/description of a course. Only the owner can update it.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Course updated", content = [Content(schema = Schema(implementation = CourseResponse::class))]),
        ApiResponse(responseCode = "400", description = "Validation failed: blank/too long name, or too long code", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "Authenticated but not the owner of this course", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "No course exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "409", description = "Caller already has a course with this name", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @PutMapping("/{id}")
    fun updateCourse(
        @Parameter(description = "Course id") @PathVariable id: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: UpdateCourseRequest,
    ): CourseResponse =
        courseService.updateCourse(id, user.id, request.name, request.code, request.description).toResponse()

    @Operation(
        summary = "Delete a course",
        description = "Deletes a course and cascades to its materials. Only the owner can delete it.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Course deleted"),
        ApiResponse(responseCode = "403", description = "Authenticated but not the owner of this course", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "404", description = "No course exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCourse(
        @Parameter(description = "Course id") @PathVariable id: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        courseService.deleteCourse(id, user.id)
    }
}
