package cz.cvut.fit.studymate.iam.internal.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ninjasquad.springmockk.MockkBean
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.internal.dto.LoginRequest
import cz.cvut.fit.studymate.iam.internal.dto.RegisterLoginResult
import cz.cvut.fit.studymate.iam.internal.dto.RegisterRequest
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.security.JwtAuthenticationFilter
import cz.cvut.fit.studymate.iam.internal.security.JwtCookies
import cz.cvut.fit.studymate.iam.internal.security.SecurityConfig
import cz.cvut.fit.studymate.iam.internal.security.accessTokenCookie
import cz.cvut.fit.studymate.iam.internal.service.AuthService
import cz.cvut.fit.studymate.iam.internal.service.JwtService
import cz.cvut.fit.studymate.iam.internal.service.TokenPair
import io.mockk.every
import io.mockk.verify
import jakarta.servlet.http.Cookie
import org.junit.jupiter.api.Test
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

// controllers = [AuthController::class] deliberately omitted: @WebMvcTest's controller
// discovery relies on classpath scanning rooted at a @SpringBootApplication/@ComponentScan,
// which this module doesn't have (only a bare @SpringBootConfiguration marker for @WebMvcTest
// to anchor on). Scanning silently finds nothing here, so every bean this slice needs —
// including the controller and its exception handler — is registered explicitly via @Import.
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

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var jwtService: JwtService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @MockkBean
    private lateinit var authService: AuthService

    private fun postJson(uri: String, body: String) =
        mockMvc.perform(post(uri).contentType(MediaType.APPLICATION_JSON).content(body))

    // ---- register ----

    @Test
    fun `register returns 201 with the created user's info and sets access and refresh cookies, without needing auth`() {
        val userId = UUID.randomUUID()
        every { authService.register("alice", "alice@example.com", "password123") } returns
            RegisterLoginResult(userId, "alice@example.com", "alice", TokenPair("acc-token", "ref-token"))

        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("alice", "password123", "alice@example.com")))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.email").value("alice@example.com"))
            .andExpect(jsonPath("$.username").value("alice"))
            .andExpect(cookie().value("access_token", "acc-token"))
            .andExpect(cookie().value("refresh_token", "ref-token"))
    }

    @Test
    fun `register returns 400 when username is blank`() {
        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("", "password123", "alice@example.com")))
            .andExpect(status().isBadRequest)
        verify(exactly = 0) { authService.register(any(), any(), any()) }
    }

    @Test
    fun `register returns 400 when username is shorter than 2 characters`() {
        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("a", "password123", "alice@example.com")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register returns 400 when username is longer than 20 characters`() {
        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("a".repeat(21), "password123", "alice@example.com")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register returns 400 when password is shorter than 8 characters`() {
        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("alice", "short12", "alice@example.com")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register returns 400 when password is longer than 30 characters`() {
        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("alice", "a".repeat(31), "alice@example.com")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register returns 400 when email is not a valid email address`() {
        postJson("/api/v1/auth/register", objectMapper.writeValueAsString(RegisterRequest("alice", "password123", "not-an-email")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `register returns 400 when the request body is missing required fields entirely`() {
        postJson("/api/v1/auth/register", "{}")
            .andExpect(status().isBadRequest)
    }

    // ---- login ----

    @Test
    fun `login returns 200 with user info and sets access and refresh cookies, without needing auth`() {
        val userId = UUID.randomUUID()
        every { authService.login("alice@example.com", "password123") } returns
            RegisterLoginResult(userId, "alice@example.com", "alice", TokenPair("acc-token", "ref-token"))

        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("alice@example.com", "password123")))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(cookie().value("access_token", "acc-token"))
            .andExpect(cookie().value("refresh_token", "ref-token"))
    }

    @Test
    fun `login returns 400 when email is blank`() {
        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("", "password123")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login returns 400 when email is not a valid email format`() {
        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("not-an-email", "password123")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login returns 400 when password is blank`() {
        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("alice@example.com", "")))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `login returns 401 with an ErrorResponse body when AuthService rejects the credentials`() {
        every { authService.login(any(), any()) } throws BadCredentialsException("Invalid credentials")

        postJson("/api/v1/auth/login", objectMapper.writeValueAsString(LoginRequest("alice@example.com", "wrong")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid credentials"))
    }

    // ---- refresh ----

    @Test
    fun `refresh returns 204 and rotates cookies when a valid refresh token cookie is present, without needing auth`() {
        every { authService.refresh("opaque-refresh-value") } returns TokenPair("new-acc", "new-ref")

        mockMvc.perform(
            post("/api/v1/auth/refresh").cookie(Cookie("refresh_token", "opaque-refresh-value"))
        )
            .andExpect(status().isNoContent)
            .andExpect(cookie().value("access_token", "new-acc"))
            .andExpect(cookie().value("refresh_token", "new-ref"))
    }

    @Test
    fun `refresh returns 401 with an ErrorResponse when no refresh token cookie is present`() {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("No refresh token provided"))

        verify(exactly = 0) { authService.refresh(any()) }
    }

    @Test
    fun `refresh returns 401 when AuthService reports the token could not be parsed or has expired`() {
        every { authService.refresh(any()) } throws InvalidTokenException("Invalid refresh token")

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(Cookie("refresh_token", "expired")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Invalid refresh token"))
    }

    @Test
    fun `refresh returns 401 when AuthService reports the token is not a refresh token`() {
        every { authService.refresh(any()) } throws InvalidTokenException("Token is not a refresh token")

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(Cookie("refresh_token", "an-access-token")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("Token is not a refresh token"))
    }

    @Test
    fun `refresh returns 401 when AuthService reports the user referenced by the token no longer exists`() {
        every { authService.refresh(any()) } throws InvalidTokenException("User no longer exists")

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(Cookie("refresh_token", "orphaned")))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.message").value("User no longer exists"))
    }

    // ---- logout (not permitAll — requires authentication) ----

    @Test
    fun `logout is rejected when no authentication cookie is present`() {
        // SecurityConfig disables formLogin/httpBasic and defines no AuthenticationEntryPoint,
        // so Spring Security falls back to Http403ForbiddenEntryPoint for unauthenticated requests.
        mockMvc.perform(post("/api/v1/auth/logout"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `logout is rejected when the access token cookie contains an invalid, unparseable JWT`() {
        // JwtAuthenticationFilter swallows the parse failure and leaves the request unauthenticated,
        // so this behaves identically to sending no cookie at all.
        mockMvc.perform(post("/api/v1/auth/logout").cookie(Cookie("access_token", "not-a-real-jwt")))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `logout returns 204 and clears both cookies when a valid access token cookie is present`() {
        val cookie = accessTokenCookie(jwtService, AuthenticatedUser(UUID.randomUUID(), "alice@example.com", Role.USER))

        mockMvc.perform(post("/api/v1/auth/logout").cookie(cookie))
            .andExpect(status().isNoContent)
            .andExpect(cookie().maxAge("access_token", 0))
            .andExpect(cookie().maxAge("refresh_token", 0))
    }
}
