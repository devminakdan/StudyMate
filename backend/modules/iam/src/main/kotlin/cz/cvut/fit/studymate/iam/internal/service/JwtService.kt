package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey
internal enum class TokenType{ ACCESS, REFRESH }

internal data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
)

internal data class TokenClaims(
    val userId: UUID,
    val email: String,
    val role: Role,
    val type: TokenType,
)

@Service
internal class JwtService(
    @Value("\${studymate.security.jwt.secret}") private val secret: String,
    @Value("\${studymate.security.jwt.access-token-ttl}") private val accessTtl: java.time.Duration,
    @Value("\${studymate.security.jwt.refresh-token-ttl}") private val refreshTtl: java.time.Duration,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateTokenPair(user: AuthenticatedUser): TokenPair {
        val now = Instant.now()
        val access = buildToken(user, TokenType.ACCESS, now, now.plus(accessTtl))
        val refresh = buildToken(user, TokenType.REFRESH, now, now.plus(refreshTtl))

        return TokenPair(access, refresh)
    }

    fun parseAndValidate(token: String): TokenClaims {
        val claims = parseClaims(token)
        val type = TokenType.valueOf(claims["type"] as String)
        val userId = UUID.fromString(claims.subject)
        val email = claims["email"] as String
        val roleString = claims["role"] as String
        val role = Role.valueOf(roleString)

        return TokenClaims(userId, email, role, type)
    }

    private fun buildToken(
        user: AuthenticatedUser,
        type: TokenType,
        issuedAt: Instant,
        expiresAt: Instant,
    ): String = Jwts.builder()
        .subject(user.id.toString())
        .claim("email", user.email)
        .claim("role", user.role.name)
        .claim("type", type.name)
        .issuedAt(Date.from(issuedAt))
        .expiration(Date.from(expiresAt))
        .signWith(key)
        .compact()

    private fun parseClaims(token: String): Claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload
}
