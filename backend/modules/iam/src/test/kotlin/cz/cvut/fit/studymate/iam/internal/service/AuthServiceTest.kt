package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.repository.AuthSession
import cz.cvut.fit.studymate.iam.internal.repository.AuthSessionRepository
import cz.cvut.fit.studymate.iam.internal.repository.RefreshTokenWithSession
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import cz.cvut.fit.studymate.iam.internal.security.UserPrincipal
import cz.cvut.fit.studymate.iam.internal.security.AccessTokenBlacklist
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.Duration
import java.time.OffsetDateTime
import java.util.UUID

internal class AuthServiceTest {

    private val users = mockk<UserRepository>()
    private val sessions = mockk<AuthSessionRepository>()
    private val jwtService = mockk<JwtService>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val authenticationManager = mockk<AuthenticationManager>()
    private val accessTokenBlacklist = mockk<AccessTokenBlacklist>()
    private val authService = AuthService(
        users, sessions, jwtService, passwordEncoder, authenticationManager, accessTokenBlacklist, Duration.ofDays(7),
    )

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "alice",
        email: String = "alice@example.com",
        role: Role = Role.USER,
    ) = User(id, username, email, role, OffsetDateTime.now(), OffsetDateTime.now())

    private fun session(userId: UUID, id: UUID = UUID.randomUUID()) = AuthSession(
        id, userId, OffsetDateTime.now(), OffsetDateTime.now(), OffsetDateTime.now().plusDays(7), null,
    )

    @Test
    fun `register persists a user and starts a new session with an opaque refresh token`() {
        val created = user()
        val authSession = session(created.id)
        every { passwordEncoder.encode("plaintext") } returns "hashed-pw"
        every { users.create("alice", "alice@example.com", "hashed-pw") } returns created
        every { sessions.createSession(created.id, null, null, any()) } returns authSession
        every { sessions.createRefreshToken(authSession.id, any(), any()) } just runs
        every { jwtService.generateAccessToken(AuthenticatedUser(created.id, created.email, created.role), authSession.id) } returns "access"

        val result = authService.register("alice", "alice@example.com", "plaintext")

        assertThat(result.accessToken).isEqualTo("access")
        assertThat(result.refreshToken).isNotBlank().isNotEqualTo("access")
        verify { sessions.createRefreshToken(authSession.id, any(), any()) }
    }

    @Test
    fun `login creates an independent session for the authenticated device`() {
        val existing = user()
        val authSession = session(existing.id)
        val principal = UserPrincipal(existing, "hashed")
        val authentication = UsernamePasswordAuthenticationToken(principal, "secret", principal.authorities)
        every { authenticationManager.authenticate(UsernamePasswordAuthenticationToken("alice@example.com", "secret")) } returns authentication
        every { sessions.createSession(existing.id, null, null, any()) } returns authSession
        every { sessions.createRefreshToken(authSession.id, any(), any()) } just runs
        every { jwtService.generateAccessToken(AuthenticatedUser(existing.id, existing.email, existing.role), authSession.id) } returns "access"

        val result = authService.login("alice@example.com", "secret")

        assertThat(result.accessToken).isEqualTo("access")
        verify { sessions.createSession(existing.id, null, null, any()) }
    }

    @Test
    fun `refresh marks the old token used and creates a replacement in the same session`() {
        val existing = user()
        val authSession = session(existing.id)
        val oldToken = RefreshTokenWithSession(UUID.randomUUID(), authSession, authSession.expiresAt, null, null)
        every { sessions.findRefreshTokenForUpdate(any()) } returns oldToken
        every { sessions.markRefreshTokenUsed(oldToken.id, any()) } returns true
        every { users.findById(existing.id) } returns existing
        every { sessions.createRefreshToken(authSession.id, any(), authSession.expiresAt) } just runs
        every { sessions.touchSession(authSession.id, any()) } just runs
        every { jwtService.generateAccessToken(AuthenticatedUser(existing.id, existing.email, existing.role), authSession.id) } returns "rotated-access"

        val result = authService.refresh("old-refresh-token")

        assertThat(result.accessToken).isEqualTo("rotated-access")
        assertThat(result.refreshToken).isNotEqualTo("old-refresh-token")
        verify { sessions.markRefreshTokenUsed(oldToken.id, any()) }
        verify { sessions.createRefreshToken(authSession.id, any(), authSession.expiresAt) }
    }

    @Test
    fun `refresh reuse revokes only the corresponding session`() {
        val authSession = session(UUID.randomUUID())
        val reused = RefreshTokenWithSession(UUID.randomUUID(), authSession, authSession.expiresAt, OffsetDateTime.now(), null)
        every { sessions.findRefreshTokenForUpdate(any()) } returns reused
        every { sessions.revokeSession(authSession.id, any()) } just runs
        every { accessTokenBlacklist.blacklistSession(authSession.id) } just runs

        assertThrows<InvalidTokenException> { authService.refresh("reused-refresh-token") }

        verify { sessions.revokeSession(authSession.id, any()) }
        verify { accessTokenBlacklist.blacklistSession(authSession.id) }
        verify(exactly = 0) { sessions.revokeAllActiveSessions(any(), any()) }
    }

    @Test
    fun `login propagates invalid credentials without creating a session`() {
        every { authenticationManager.authenticate(any()) } throws BadCredentialsException("Bad credentials")

        assertThrows<BadCredentialsException> {
            authService.login("nobody@example.com", "wrong")
        }

        verify(exactly = 0) { sessions.createSession(any(), any(), any(), any()) }
    }
}
