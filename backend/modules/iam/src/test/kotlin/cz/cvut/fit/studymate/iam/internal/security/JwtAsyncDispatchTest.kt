package cz.cvut.fit.studymate.iam.internal.security

import com.ninjasquad.springmockk.MockkBean
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.internal.service.JwtService
import io.mockk.every
import jakarta.servlet.http.Cookie
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@WebMvcTest
@Import(
    AsyncSecurityTestController::class,
    SecurityConfig::class,
    JwtAuthenticationFilter::class,
    JwtService::class,
    JwtCookies::class,
)
internal class JwtAsyncDispatchTest {
    @Autowired private lateinit var mvc: MockMvc
    @Autowired private lateinit var jwtService: JwtService
    @MockkBean private lateinit var blacklist: AccessTokenBlacklist

    @BeforeEach
    fun allowAccessTokens() {
        every { blacklist.isBlacklisted(any()) } returns false
    }

    @Test
    fun `valid JWT remains authenticated when a suspended controller finishes through async dispatch`() {
        val user = AuthenticatedUser(UUID.randomUUID(), "test@example.com", Role.USER)

        val initial = mvc.perform(get("/test/async-security").cookie(accessTokenCookie(jwtService, user)))
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(user.id.toString()))
            .andExpect(jsonPath("$.email").value(user.email))
            .andExpect(jsonPath("$.role").value("USER"))

        assertThat(initial.request.getSession(false)).isNull()
        mvc.perform(get("/test/async-security"))
            .andExpect(status().isForbidden)
    }

    @Test
    fun `missing or invalid JWT is rejected before async processing with an empty response body`() {
        mvc.perform(get("/test/async-security"))
            .andExpect(status().isForbidden)
            .andExpect(request().asyncNotStarted())
            .andExpect(content().string(""))

        mvc.perform(get("/test/async-security").cookie(Cookie(JwtCookies.ACCESS_COOKIE, "invalid")))
            .andExpect(status().isForbidden)
            .andExpect(request().asyncNotStarted())
            .andExpect(content().string(""))
    }

    @Test
    fun `revoked JWT is rejected before async processing`() {
        every { blacklist.isBlacklisted(any()) } returns true
        val user = AuthenticatedUser(UUID.randomUUID(), "test@example.com", Role.USER)

        mvc.perform(get("/test/async-security").cookie(accessTokenCookie(jwtService, user)))
            .andExpect(status().isForbidden)
            .andExpect(request().asyncNotStarted())
    }
}

@RestController
internal class AsyncSecurityTestController {
    @GetMapping("/test/async-security")
    suspend fun search(@AuthenticationPrincipal user: AuthenticatedUser): AuthenticatedUser {
        delay(10)
        return user
    }
}
