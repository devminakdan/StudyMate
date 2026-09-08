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

internal data class TokenClaims(
    val userId: UUID,
    val email: String,
    val role: Role,
    val sessionId: UUID,
    val expiresAt: Instant,
)

@Service
internal class JwtService(
    @Value("\${studymate.security.jwt.secret}") private val secret: String,
    @Value("\${studymate.security.jwt.access-token-ttl}") private val accessTtl: java.time.Duration,
) {
    private val key: SecretKey = Keys.hmacShaKeyFor(secret.toByteArray())

    fun generateAccessToken(user: AuthenticatedUser, sessionId: UUID): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("sid", sessionId.toString())
            // These claims preserve the existing role-based authorization without a database
            // lookup on every request. The session id binds this token to its server session.
            .claim("email", user.email)
            .claim("role", user.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(accessTtl)))
            .signWith(key)
            .compact()
    }

    fun parseAndValidate(token: String): TokenClaims {
        val claims = parseClaims(token)
        val userId = UUID.fromString(claims.subject)
        val sessionId = UUID.fromString(claims["sid"] as String)
        val email = claims["email"] as String
        val roleString = claims["role"] as String
        val role = Role.valueOf(roleString)

        return TokenClaims(userId, email, role, sessionId, claims.expiration.toInstant())
    }

    private fun parseClaims(token: String): Claims = Jwts.parser()
        .verifyWith(key)
        .build()
        .parseSignedClaims(token)
        .payload
}
