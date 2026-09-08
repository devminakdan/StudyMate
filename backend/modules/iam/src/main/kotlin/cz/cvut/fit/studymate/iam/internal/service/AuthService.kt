package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.internal.dto.AuthResult
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.repository.AuthSessionRepository
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import cz.cvut.fit.studymate.iam.internal.security.UserPrincipal
import cz.cvut.fit.studymate.iam.internal.security.AccessTokenBlacklist
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.beans.factory.annotation.Value
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64

@Service
internal class AuthService(
    private val repository: UserRepository,
    private val authSessionRepository: AuthSessionRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
    private val accessTokenBlacklist: AccessTokenBlacklist,
    @Value("\${studymate.security.jwt.refresh-token-ttl}") private val refreshTtl: java.time.Duration,
) {
    @Transactional
    fun register(
        username: String,
        email: String,
        password: String,
    ): AuthResult {
        val passwordHash = passwordEncoder.encode(password)
        val user = repository.create(username, email, passwordHash)
        return createSessionAndTokens(user.id, user.email, user.username, user.role)
    }

    @Transactional
    fun login(email: String, password: String): AuthResult {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(email, password)
        )
        val user = (authentication.principal as UserPrincipal).user

        return createSessionAndTokens(user.id, user.email, user.username, user.role)
    }

    @Transactional(noRollbackFor = [InvalidTokenException::class])
    fun refresh(refreshToken: String): AuthResult {
        val now = OffsetDateTime.now()
        val token = authSessionRepository.findRefreshTokenForUpdate(hashRefreshToken(refreshToken))
            ?: throw InvalidTokenException("Invalid refresh token")

        if (token.usedAt != null) {
            revokeSession(token.session.id, now)
            throw InvalidTokenException("Refresh token reuse detected")
        }
        if (token.revokedAt != null || token.expiresAt <= now ||
            token.session.revokedAt != null || token.session.expiresAt <= now
        ) {
            throw InvalidTokenException("Invalid refresh token")
        }

        // The lock plus this conditional update gives the database the final say even if this
        // repository is later reused without the locking read.
        if (!authSessionRepository.markRefreshTokenUsed(token.id, now)) {
            revokeSession(token.session.id, now)
            throw InvalidTokenException("Refresh token reuse detected")
        }

        val user = repository.findById(token.session.userId)
            ?: run {
                revokeSession(token.session.id, now)
                throw InvalidTokenException("Invalid refresh token")
            }
        val newRefreshToken = generateRefreshToken()
        authSessionRepository.createRefreshToken(
            token.session.id,
            hashRefreshToken(newRefreshToken),
            token.session.expiresAt,
        )
        authSessionRepository.touchSession(token.session.id, now)

        return AuthResult(
            user.id,
            user.email,
            user.username,
            jwtService.generateAccessToken(AuthenticatedUser(user.id, user.email, user.role), token.session.id),
            newRefreshToken,
        )
    }

    @Transactional
    fun logout(refreshToken: String) {
        val token = authSessionRepository.findRefreshTokenForUpdate(hashRefreshToken(refreshToken))
            ?: throw InvalidTokenException("Invalid refresh token")
        revokeSession(token.session.id, OffsetDateTime.now())
    }

    @Transactional
    fun logoutAll(userId: java.util.UUID) {
        authSessionRepository.revokeAllActiveSessions(userId, OffsetDateTime.now())
            .forEach(accessTokenBlacklist::blacklistSession)
    }

    private fun createSessionAndTokens(
        userId: java.util.UUID,
        email: String,
        username: String,
        role: Role,
    ): AuthResult {
        val expiresAt = OffsetDateTime.now().plus(refreshTtl)
        val session = authSessionRepository.createSession(userId, null, null, expiresAt)
        val refreshToken = generateRefreshToken()
        authSessionRepository.createRefreshToken(session.id, hashRefreshToken(refreshToken), expiresAt)
        return AuthResult(
            userId,
            email,
            username,
            jwtService.generateAccessToken(AuthenticatedUser(userId, email, role), session.id),
            refreshToken,
        )
    }

    private fun generateRefreshToken(): String = ByteArray(REFRESH_TOKEN_BYTES)
        .also(secureRandom::nextBytes)
        .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }

    private fun hashRefreshToken(token: String): String = MessageDigest
        .getInstance("SHA-256")
        .digest(token.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun revokeSession(sessionId: java.util.UUID, now: OffsetDateTime) {
        authSessionRepository.revokeSession(sessionId, now)
        accessTokenBlacklist.blacklistSession(sessionId)
    }

    private companion object {
        const val REFRESH_TOKEN_BYTES = 32
        val secureRandom = SecureRandom()
    }
}
