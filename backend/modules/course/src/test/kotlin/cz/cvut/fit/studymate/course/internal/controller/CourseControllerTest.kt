package cz.cvut.fit.studymate.course.internal.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.internal.dto.CreateCourseRequest
import cz.cvut.fit.studymate.course.internal.dto.UpdateCourseRequest
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseAlreadyExistsException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import cz.cvut.fit.studymate.course.internal.service.CourseService
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest
@Import(CourseController::class, CourseExceptionHandler::class, CourseControllerTest.MinimalSecurityConfig::class)
internal class CourseControllerTest {

    // Exists only so Spring Security registers AuthenticationPrincipalArgumentResolver
    // (needed for @AuthenticationPrincipal) and so .with(authentication(...)) has a real
    // filter chain to run through. CSRF disabled to mirror the real SecurityConfig.
    @TestConfiguration
    class MinimalSecurityConfig {
        @Bean
        fun filterChain(http: HttpSecurity): SecurityFilterChain =
            http.csrf { it.disable() }.build()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var service: CourseService

    private fun course(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        name: String = "Programování a algoritmizace 2",
        code: String? = "BI-PA2.21",
        description: String? = null,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
    ) = Course(id, ownerId, name, code, description, createdAt, updatedAt)

    private fun authenticatedAs(userId: UUID) = authentication(
        UsernamePasswordAuthenticationToken(
            AuthenticatedUser(userId, "test@example.com", Role.USER),
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    )

    // ---- POST /api/v1/courses ----

    @Test
    fun `createCourse returns 201 with the created course for a valid body`() {
        val userId = UUID.randomUUID()
        val created = course(ownerId = userId)

        every { service.createCourse(userId, created.name, created.code, created.description) } returns created

        val requestBody = objectMapper.writeValueAsString(
            CreateCourseRequest(
                created.name,
                created.code,
                created.description
            )
        )

        mockMvc.perform(post("/api/v1/courses")
                .with(authenticatedAs(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value(created.name))
            .andExpect(jsonPath("$.ownerId").value(userId.toString()))
    }

    @Test
    fun `createCourse returns 400 when name is blank`() {
        val userId = UUID.randomUUID()
        val requestBody = objectMapper.writeValueAsString(CreateCourseRequest("", "BI-PA2.21", null))

        mockMvc.perform(
            post("/api/v1/courses")
                .with(authenticatedAs(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { service.createCourse(any(), any(), any(), any()) }
    }

    @Test
    fun `createCourse returns 409 when the service reports a duplicate name`() {
        val userId = UUID.randomUUID()
        val requestBody = objectMapper.writeValueAsString(CreateCourseRequest("Programování a algoritmizace 2", "BI-PA2.21", null))
        every {
            service.createCourse(userId, "Programování a algoritmizace 2", "BI-PA2.21", null)
        } throws CourseAlreadyExistsException("You already have a course named 'Programování a algoritmizace 2'")

        mockMvc.perform(
            post("/api/v1/courses")
                .with(authenticatedAs(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isConflict)
    }

    // ---- GET /api/v1/courses ----

    @Test
    fun `listCourses returns 200 with a page of courses and the X-Total-Count header`() {
        val userId = UUID.randomUUID()
        val courses = listOf(course(ownerId = userId), course(ownerId = userId))
        every { service.listCourses(userId, 0, 20) } returns courses
        every { service.countCourses(userId) } returns 5

        mockMvc.perform(
            get("/api/v1/courses")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "5"))
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value(courses[0].name))
    }

    // ---- GET /api/v1/courses/{id} ----

    @Test
    fun `getCourse returns 200 with the course when found and owned by the caller`() {
        val userId = UUID.randomUUID()
        val existing = course(ownerId = userId)
        every { service.getCourse(existing.id, userId) } returns existing

        mockMvc.perform(
            get("/api/v1/courses/${existing.id}")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(existing.id.toString()))
            .andExpect(jsonPath("$.name").value(existing.name))
    }

    @Test
    fun `getCourse returns 404 when the service reports the course does not exist`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        every { service.getCourse(courseId, userId) } throws CourseNotFoundException(courseId)

        mockMvc.perform(
            get("/api/v1/courses/$courseId")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Course not found: $courseId"))
    }

    @Test
    fun `getCourse returns 403 when the service reports the caller does not own the course`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        every { service.getCourse(courseId, userId) } throws CourseAccessDeniedException("You don't own course $courseId")

        mockMvc.perform(
            get("/api/v1/courses/$courseId")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isForbidden)
    }

    // ---- PUT /api/v1/courses/{id} ----

    @Test
    fun `updateCourse returns 200 with the updated course for a valid body`() {
        val userId = UUID.randomUUID()
        val updated = course(ownerId = userId, name = "New name")
        every { service.updateCourse(updated.id, userId, "New name", updated.code, updated.description) } returns updated

        val requestBody = objectMapper.writeValueAsString(UpdateCourseRequest("New name", updated.code, updated.description))

        mockMvc.perform(
            put("/api/v1/courses/${updated.id}")
                .with(authenticatedAs(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New name"))
    }

    @Test
    fun `updateCourse returns 400 when name is blank`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val requestBody = objectMapper.writeValueAsString(UpdateCourseRequest("", "BI-PA2.21", null))

        mockMvc.perform(
            put("/api/v1/courses/$courseId")
                .with(authenticatedAs(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody)
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { service.updateCourse(any(), any(), any(), any(), any()) }
    }

    // ---- DELETE /api/v1/courses/{id} ----

    @Test
    fun `deleteCourse returns 204 when the caller is the owner`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        every { service.deleteCourse(courseId, userId) } just Runs

        mockMvc.perform(
            delete("/api/v1/courses/$courseId")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { service.deleteCourse(courseId, userId) }
    }
}
