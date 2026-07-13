package cz.cvut.fit.studymate.course.internal.controller

import cz.cvut.fit.studymate.course.internal.dto.CourseResponse
import cz.cvut.fit.studymate.course.internal.dto.CreateCourseRequest
import cz.cvut.fit.studymate.course.internal.dto.UpdateCourseRequest
import cz.cvut.fit.studymate.course.internal.dto.toResponse
import cz.cvut.fit.studymate.course.internal.service.CourseService
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
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

@RestController
@RequestMapping("/api/v1/courses")
internal class CourseController(
    private val courseService: CourseService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCourse(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: CreateCourseRequest,
    ): CourseResponse =
        courseService.createCourse(user.id, request.name, request.code, request.description).toResponse()

    @GetMapping
    fun listCourses(
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<CourseResponse>> {
        val courses = courseService.listCourses(user.id, page.coerceAtLeast(0), size.coerceIn(1, 100))
        val total = courseService.countCourses(user.id)
        return ResponseEntity.ok()
            .header("X-Total-Count", total.toString())
            .body(courses.map { it.toResponse() })
    }

    @GetMapping("/{id}")
    fun getCourse(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): CourseResponse =
        courseService.getCourse(id, user.id).toResponse()

    @PutMapping("/{id}")
    fun updateCourse(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: UpdateCourseRequest,
    ): CourseResponse =
        courseService.updateCourse(id, user.id, request.name, request.code, request.description).toResponse()

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCourse(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        courseService.deleteCourse(id, user.id)
    }
}
