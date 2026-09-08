package cz.cvut.fit.studymate.iam.internal.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.internal.dto.AuthResult
import cz.cvut.fit.studymate.iam.internal.dto.LoginRequest
import cz.cvut.fit.studymate.iam.internal.dto.RegisterRequest
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.security.JwtAuthenticationFilter
import cz.cvut.fit.studymate.iam.internal.security.JwtCookies
import cz.cvut.fit.studymate.iam.internal.security.AccessTokenBlacklist
import cz.cvut.fit.studymate.iam.internal.security.SecurityConfig
import cz.cvut.fit.studymate.iam.internal.security.accessTokenCookie
import cz.cvut.fit.studymate.iam.internal.service.AuthService
import cz.cvut.fit.studymate.iam.internal.service.JwtService
import io.mockk.every
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

@WebMvcTest
@Import(
    AuthController::class,
    AuthExceptionHandler::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtService::class,
    JwtCookies::class,
)
internal class AuthControllerTest {

    @Autowired lateinit var mockMvc: MockMvc
    @Autowired lateinit var objectMapper: ObjectMapper
    @Autowired lateinit var jwtService: JwtService
    @MockkBean lateinit var authService: AuthService
    @MockkBean lateinit var accessTokenBlacklist: AccessTokenBlacklist

    private fun postJson(uri: String, body: String) =
        mockMvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(body))

    private fun result(userId: UUID = UUID.randomUUID()) =
        AuthResult(userId, "alice@example.com", "alice", "access-token", "opaque-refresh-token")

    @BeforeEach
    fun allowAccessTokens() {
        every { accessTokenBlacklist.isBlacklisted(any()) } returns false
    }

    @Test
    fun `register keeps both tokens in HttpOnly cookies`() {
        val result = result()
        every { authService.register("alice", "alice@example.com", "password123") } returns result

        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("alice", "password123", "alice@example.com")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(result.userId.toString()))
            .andExpect(cookie().value("access_token", "access-token"))
            .andExpect(cookie().value("refresh_token", "opaque-refresh-token"))
    }

    @Test
    fun `login keeps both tokens in HttpOnly cookies`() {
        val result = result()
        every { authService.login("alice@example.com", "password123") } returns result

        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("alice@example.com", "password123")))
            .andExpect(status().isOk)
            .andExpect(cookie().value("access_token", "access-token"))
            .andExpect(cookie().value("refresh_token", "opaque-refresh-token"))
    }

    @Test
    fun `login returns 401 for invalid credentials`() {
        every { authService.login(any(), any()) } throws BadCredentialsException("Invalid credentials")

        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("alice@example.com", "wrong")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
    }

    @Test
    fun `refresh replaces both token cookies`() {
        every { authService.refresh("old-refresh") } returns result().copy(accessToken = "new-access", refreshToken = "new-refresh")

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(Cookie("refresh_token", "old-refresh")))
            .andExpect(status().isNoContent)
            .andExpect(cookie().value("access_token", "new-access"))
            .andExpect(cookie().value("refresh_token", "new-refresh"))
    }

    @Test
    fun `refresh rejects a missing or reused cookie`() {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized)

        every { authService.refresh("reused") } throws InvalidTokenException("Refresh token reuse detected")
        mockMvc.perform(post("/api/v1/auth/refresh").cookie(Cookie("refresh_token", "reused")))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `logout revokes the session identified by refresh cookie and clears it`() {
        every { authService.logout("refresh") } returns Unit

        mockMvc.perform(post("/api/v1/auth/logout").cookie(Cookie("refresh_token", "refresh")))
            .andExpect(status().isNoContent)
            .andExpect(cookie().maxAge("access_token", 0))
            .andExpect(cookie().maxAge("refresh_token", 0))
        verify { authService.logout("refresh") }
    }

    @Test
    fun `logout all requires access_token cookie`() {
        val user = AuthenticatedUser(UUID.randomUUID(), "alice@example.com", Role.USER)
        every { authService.logoutAll(user.id) } returns Unit

        mockMvc.perform(post("/api/v1/auth/logout-all"))
            .andExpect(status().isForbidden)
        mockMvc.perform(post("/api/v1/auth/logout-all").cookie(accessTokenCookie(jwtService, user)))
            .andExpect(status().isNoContent)
        verify { authService.logoutAll(user.id) }
    }
}
