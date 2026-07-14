package cz.cvut.fit.studymate.iam.internal.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.internal.dto.ChangeRoleRequest
import cz.cvut.fit.studymate.iam.internal.exception.UserNotFoundException
import cz.cvut.fit.studymate.iam.internal.security.JwtAuthenticationFilter
import cz.cvut.fit.studymate.iam.internal.security.JwtCookies
import cz.cvut.fit.studymate.iam.internal.security.SecurityConfig
import cz.cvut.fit.studymate.iam.internal.security.accessTokenCookie
import cz.cvut.fit.studymate.iam.internal.service.JwtService
import cz.cvut.fit.studymate.iam.internal.service.UserService
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.OffsetDateTime
import java.util.UUID

// controllers = [UserController::class] deliberately omitted — see AuthControllerTest for why:
// @WebMvcTest's scanning-based controller discovery finds nothing in this module, so every bean
// this slice needs is registered explicitly via @Import instead.
@WebMvcTest
@Import(
    UserController::class,
    AuthExceptionHandler::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtService::class,
    JwtCookies::class,
)
internal class UserControllerTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var service: UserService

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "alice",
        email: String = "alice@example.com",
        role: Role = Role.USER,
    ) = User(id, username, email, role, OffsetDateTime.now(), OffsetDateTime.now())

    // ---- GET /me ----

    @Test
    fun `getMe is rejected when no authentication cookie is present`() {
        // SecurityConfig has no AuthenticationEntryPoint bean, so Spring Security's
        // Http403ForbiddenEntryPoint default applies to unauthenticated requests.
        mockMvc.perform(get("/me"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `getMe returns 200 with the current user's data when authenticated as USER`() {
        val existing = user(role = Role.USER)
        every { service.findById(existing.id) } returns existing

        mockMvc.perform(get("/me").cookie(accessTokenCookie(jwtService, AuthenticatedUser(existing.id, existing.email, existing.role))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(existing.id.toString()))
            .andExpect(jsonPath("$.email").value(existing.email))
            .andExpect(jsonPath("$.username").value(existing.username))
            .andExpect(jsonPath("$.role").value("USER"))
    }

    @Test
    fun `getMe returns 200 with the current user's data when authenticated as ADMIN`() {
        val existing = user(role = Role.ADMIN)
        every { service.findById(existing.id) } returns existing

        mockMvc.perform(get("/me").cookie(accessTokenCookie(jwtService, AuthenticatedUser(existing.id, existing.email, existing.role))))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `getMe returns 404 with an ErrorResponse when the authenticated principal's user record no longer exists`() {
        val id = UUID.randomUUID()
        every { service.findById(id) } throws UserNotFoundException(id)

        mockMvc.perform(get("/me").cookie(accessTokenCookie(jwtService, AuthenticatedUser(id, "alice@example.com", Role.USER))))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("User with id $id not found"))
    }

    // ---- PATCH /api/v1/users/{id}/role ----

    @Test
    fun `changeRole is rejected when no authentication cookie is present`() {
        mockMvc.perform(
            patch("/api/v1/users/${UUID.randomUUID()}/role")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ChangeRoleRequest(Role.ADMIN)))
        )
            .andExpect(status().isForbidden)

        verify(exactly = 0) { service.changeRole(any(), any()) }
    }

    @Test
    fun `changeRole returns 403 when authenticated as USER (insufficient role for hasRole ADMIN)`() {
        val callerId = UUID.randomUUID()

        mockMvc.perform(
            patch("/api/v1/users/${UUID.randomUUID()}/role")
                .cookie(accessTokenCookie(jwtService, AuthenticatedUser(callerId, "alice@example.com", Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ChangeRoleRequest(Role.ADMIN)))
        )
            .andExpect(status().isForbidden)

        verify(exactly = 0) { service.changeRole(any(), any()) }
    }

    @Test
    fun `changeRole returns 200 with the updated user when authenticated as ADMIN and the body is valid`() {
        val adminId = UUID.randomUUID()
        val targetId = UUID.randomUUID()
        val updated = user(id = targetId, role = Role.ADMIN)
        every { service.changeRole(targetId, Role.ADMIN) } returns updated

        mockMvc.perform(
            patch("/api/v1/users/$targetId/role")
                .cookie(accessTokenCookie(jwtService, AuthenticatedUser(adminId, "admin@example.com", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ChangeRoleRequest(Role.ADMIN)))
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(targetId.toString()))
            .andExpect(jsonPath("$.role").value("ADMIN"))
    }

    @Test
    fun `changeRole returns 400 when the role field is missing from the request body`() {
        mockMvc.perform(
            patch("/api/v1/users/${UUID.randomUUID()}/role")
                .cookie(accessTokenCookie(jwtService, AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}")
        )
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { service.changeRole(any(), any()) }
    }

    @Test
    fun `changeRole returns 400 when the role field is not a valid enum value`() {
        mockMvc.perform(
            patch("/api/v1/users/${UUID.randomUUID()}/role")
                .cookie(accessTokenCookie(jwtService, AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"role":"SUPERUSER"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `changeRole returns 404 with an ErrorResponse when UserService reports the target user does not exist`() {
        val targetId = UUID.randomUUID()
        every { service.changeRole(targetId, Role.ADMIN) } throws UserNotFoundException(targetId)

        mockMvc.perform(
            patch("/api/v1/users/$targetId/role")
                .cookie(accessTokenCookie(jwtService, AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ChangeRoleRequest(Role.ADMIN)))
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("User with id $targetId not found"))
    }

    @Test
    fun `changeRole returns 400 when the id path variable is not a valid UUID`() {
        mockMvc.perform(
            patch("/api/v1/users/not-a-uuid/role")
                .cookie(accessTokenCookie(jwtService, AuthenticatedUser(UUID.randomUUID(), "admin@example.com", Role.ADMIN)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(ChangeRoleRequest(Role.ADMIN)))
        )
            .andExpect(status().isBadRequest)
    }
}
