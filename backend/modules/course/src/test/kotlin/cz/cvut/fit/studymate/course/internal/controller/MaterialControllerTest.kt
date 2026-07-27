package cz.cvut.fit.studymate.course.internal.controller

import com.ninjasquad.springmockk.MockkBean
import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import cz.cvut.fit.studymate.course.internal.exception.InvalidMaterialException
import cz.cvut.fit.studymate.course.internal.exception.MaterialNotFoundException
import cz.cvut.fit.studymate.course.internal.service.MaterialService
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.security.web.SecurityFilterChain
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.util.UUID

@WebMvcTest
@Import(MaterialController::class, CourseExceptionHandler::class, MaterialControllerTest.MinimalSecurityConfig::class)
internal class MaterialControllerTest {

    @TestConfiguration
    class MinimalSecurityConfig {
        @Bean
        fun filterChain(http: HttpSecurity): SecurityFilterChain =
            http.csrf { it.disable() }.build()
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var service: MaterialService

    private fun material(
        id: UUID = UUID.randomUUID(),
        courseId: UUID = UUID.randomUUID(),
    ) = Material(
        id, courseId, "lecture.pdf", "materials/$courseId/generated.pdf", "application/pdf",
        1024L, MaterialStatus.PENDING, null, OffsetDateTime.now(), null, null
    )

    private fun authenticatedAs(userId: UUID) = authentication(
        UsernamePasswordAuthenticationToken(
            AuthenticatedUser(userId, "test@example.com", Role.USER),
            null,
            listOf(SimpleGrantedAuthority("ROLE_USER")),
        )
    )

    // ---- POST /api/v1/courses/{courseId}/materials ----

    @Test
    fun `uploadMaterial returns 202 with the created material for a valid upload`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val created = material(courseId = courseId)
        val file = MockMultipartFile("file", "lecture.pdf", "application/pdf", "%PDF-1.4".toByteArray())

        every { service.uploadMaterial(courseId, userId, any()) } returns created

        mockMvc.perform(
            multipart("/api/v1/courses/$courseId/materials")
                .file(file)
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isAccepted)
            .andExpect(jsonPath("$.id").value(created.id.toString()))
            .andExpect(jsonPath("$.originalFilename").value(created.originalFilename))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `uploadMaterial returns 400 when the service reports an invalid file`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val file = MockMultipartFile("file", "empty.pdf", "application/pdf", ByteArray(0))
        every { service.uploadMaterial(courseId, userId, any()) } throws InvalidMaterialException("File is empty")

        mockMvc.perform(
            multipart("/api/v1/courses/$courseId/materials")
                .file(file)
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `uploadMaterial returns 403 when the caller doesn't own the course`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val file = MockMultipartFile("file", "lecture.pdf", "application/pdf", "%PDF-1.4".toByteArray())
        every { service.uploadMaterial(courseId, userId, any()) } throws CourseAccessDeniedException("You don't own course $courseId")

        mockMvc.perform(
            multipart("/api/v1/courses/$courseId/materials")
                .file(file)
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isForbidden)
    }

    @Test
    fun `uploadMaterial returns 404 when the service reports the course does not exist`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val file = MockMultipartFile("file", "lecture.pdf", "application/pdf", "%PDF-1.4".toByteArray())
        every { service.uploadMaterial(courseId, userId, any()) } throws CourseNotFoundException(courseId)

        mockMvc.perform(
            multipart("/api/v1/courses/$courseId/materials")
                .file(file)
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isNotFound)
    }

    // ---- GET /api/v1/courses/{courseId}/materials ----

    @Test
    fun `listMaterials returns 200 with a page of materials and the X-Total-Count header`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val materials = listOf(material(courseId = courseId), material(courseId = courseId))
        every { service.listMaterials(courseId, userId, 0, 20) } returns materials
        every { service.countMaterials(courseId, userId) } returns 5

        mockMvc.perform(
            get("/api/v1/courses/$courseId/materials")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isOk)
            .andExpect(header().string("X-Total-Count", "5"))
            .andExpect(jsonPath("$.length()").value(2))
    }

    // ---- GET /api/v1/courses/{courseId}/materials/{materialId} ----

    @Test
    fun `getMaterial returns 200 with the material when found`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val existing = material(courseId = courseId)
        every { service.getMaterial(courseId, existing.id, userId) } returns existing

        mockMvc.perform(
            get("/api/v1/courses/$courseId/materials/${existing.id}")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(existing.id.toString()))
    }

    @Test
    fun `getMaterial returns 404 when the service reports the material does not exist`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        every { service.getMaterial(courseId, materialId, userId) } throws MaterialNotFoundException(materialId)

        mockMvc.perform(
            get("/api/v1/courses/$courseId/materials/$materialId")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isNotFound)
    }

    // ---- DELETE /api/v1/courses/{courseId}/materials/{materialId} ----

    @Test
    fun `deleteMaterial returns 204 when the caller is the owner`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        every { service.deleteMaterial(courseId, materialId, userId) } just Runs

        mockMvc.perform(
            delete("/api/v1/courses/$courseId/materials/$materialId")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isNoContent)

        verify(exactly = 1) { service.deleteMaterial(courseId, materialId, userId) }
    }

    @Test
    fun `deleteMaterial returns 404 when the service reports the material does not exist`() {
        val userId = UUID.randomUUID()
        val courseId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        every { service.deleteMaterial(courseId, materialId, userId) } throws MaterialNotFoundException(materialId)

        mockMvc.perform(
            delete("/api/v1/courses/$courseId/materials/$materialId")
                .with(authenticatedAs(userId))
        )
            .andExpect(status().isNotFound)
    }
}
