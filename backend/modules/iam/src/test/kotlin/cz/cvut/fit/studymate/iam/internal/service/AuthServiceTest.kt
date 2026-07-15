package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import cz.cvut.fit.studymate.iam.internal.security.UserPrincipal
import io.jsonwebtoken.MalformedJwtException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.OffsetDateTime
import java.util.UUID

internal class AuthServiceTest {

    private val repository = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val passwordEncoder = mockk<PasswordEncoder>()
    private val authenticationManager = mockk<AuthenticationManager>()
    private val authService = AuthService(repository, jwtService, passwordEncoder, authenticationManager)

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "alice",
        email: String = "alice@example.com",
        role: Role = Role.USER,
    ) = User(id, username, email, role, OffsetDateTime.now(), OffsetDateTime.now())

    @Test
    fun `register hashes the raw password, persists the user, and returns tokens with the persisted ids`() {
        val created = user()
        val tokens = TokenPair("access-token", "refresh-token")

        every { passwordEncoder.encode("plaintext") } returns "hashed-pw"
        every { repository.create("alice", "alice@example.com", "hashed-pw") } returns created
        every { jwtService.generateTokenPair(AuthenticatedUser(created.id, created.email, created.role)) } returns tokens

        val result = authService.register("alice", "alice@example.com", "plaintext")

        assertThat(result.userId).isEqualTo(created.id)
        assertThat(result.email).isEqualTo(created.email)
        assertThat(result.username).isEqualTo(created.username)
        assertThat(result.tokens).isEqualTo(tokens)

        verify(exactly = 1) { passwordEncoder.encode("plaintext") }
        verify(exactly = 1) { repository.create("alice", "alice@example.com", "hashed-pw") }
    }

    @Test
    fun `register propagates the exception thrown by the repository (e-g- duplicate-email constraint violation)`() {
        every { passwordEncoder.encode(any()) } returns "hashed-pw"
        every { repository.create(any(), any(), any()) } throws DataIntegrityViolationException("duplicate email")

        assertThrows<DataIntegrityViolationException> {
            authService.register("alice", "alice@example.com", "plaintext")
        }

        verify(exactly = 0) { jwtService.generateTokenPair(any()) }
    }

    @Test
    fun `login authenticates via AuthenticationManager and returns tokens for the resulting principal`() {
        val existing = user()
        val tokens = TokenPair("access-token", "refresh-token")
        val principal = UserPrincipal(existing, "hashed")
        val authentication = UsernamePasswordAuthenticationToken(principal, "secret", principal.authorities)

        every { authenticationManager.authenticate(UsernamePasswordAuthenticationToken("alice@example.com", "secret")) } returns authentication
        every { jwtService.generateTokenPair(AuthenticatedUser(existing.id, existing.email, existing.role)) } returns tokens

        val result = authService.login("alice@example.com", "secret")

        assertThat(result.userId).isEqualTo(existing.id)
        assertThat(result.email).isEqualTo(existing.email)
        assertThat(result.username).isEqualTo(existing.username)
        assertThat(result.tokens).isEqualTo(tokens)
    }

    @Test
    fun `login propagates BadCredentialsException from AuthenticationManager (unknown email or wrong password alike)`() {
        every { authenticationManager.authenticate(any()) } throws BadCredentialsException("Bad credentials")

        assertThrows<BadCredentialsException> {
            authService.login("nobody@example.com", "wrong")
        }

        verify(exactly = 0) { jwtService.generateTokenPair(any()) }
    }

    @Test
    fun `refresh returns a new token pair for a valid, unexpired refresh token`() {
        val userId = UUID.randomUUID()
        val email = "alice@example.com"
        val claims = TokenClaims(userId, email, Role.USER, TokenType.REFRESH)
        val existing = user(id = userId, email = email)
        val newTokens = TokenPair("new-access", "new-refresh")

        every { jwtService.parseAndValidate("refresh-token") } returns claims
        every { repository.findById(userId) } returns existing
        every { jwtService.generateTokenPair(AuthenticatedUser(userId, email, Role.USER)) } returns newTokens

        val result = authService.refresh("refresh-token")

        assertThat(result).isEqualTo(newTokens)
    }

    @Test
    fun `refresh throws InvalidTokenException when JwtService rejects the token as malformed or expired`() {
        every { jwtService.parseAndValidate("bad-token") } throws MalformedJwtException("bad token")

        val ex = assertThrows<InvalidTokenException> { authService.refresh("bad-token") }
        assertThat(ex.message).isEqualTo("Invalid refresh token")

        verify(exactly = 0) { repository.findById(any()) }
    }

    @Test
    fun `refresh throws InvalidTokenException when the token's type claim is ACCESS instead of REFRESH`() {
        val claims = TokenClaims(UUID.randomUUID(), "alice@example.com", Role.USER, TokenType.ACCESS)
        every { jwtService.parseAndValidate("access-token") } returns claims

        val ex = assertThrows<InvalidTokenException> { authService.refresh("access-token") }
        assertThat(ex.message).isEqualTo("Token is not a refresh token")

        verify(exactly = 0) { repository.findById(any()) }
    }

    @Test
    fun `refresh throws InvalidTokenException when the user referenced by the token has since been deleted`() {
        val userId = UUID.randomUUID()
        val claims = TokenClaims(userId, "alice@example.com", Role.USER, TokenType.REFRESH)

        every { jwtService.parseAndValidate("refresh-token") } returns claims
        every { repository.findById(userId) } returns null

        val ex = assertThrows<InvalidTokenException> { authService.refresh("refresh-token") }
        assertThat(ex.message).isEqualTo("User no longer exists")

        verify(exactly = 0) { jwtService.generateTokenPair(any()) }
    }

    @Test
    fun `refresh lets a non-JwtException from JwtService propagate uncaught (e-g- a corrupt claim value)`() {
        every { jwtService.parseAndValidate("corrupt-token") } throws IllegalArgumentException("bad uuid")

        assertThrows<IllegalArgumentException> { authService.refresh("corrupt-token") }
    }
}
