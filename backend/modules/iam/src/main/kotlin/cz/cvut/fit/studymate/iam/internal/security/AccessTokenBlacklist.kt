package cz.cvut.fit.studymate.iam.internal.security

import cz.cvut.fit.studymate.iam.internal.service.TokenClaims
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.UUID

/** Blocks all unexpired access JWTs belonging to a revoked server-side session. */
internal interface AccessTokenBlacklist {
    fun blacklistSession(sessionId: UUID)
    fun isBlacklisted(claims: TokenClaims): Boolean
}

@Component
internal class RedisAccessTokenBlacklist(
    private val redisTemplate: StringRedisTemplate,
    @Value("\${studymate.security.jwt.access-token-ttl}") private val accessTokenTtl: Duration,
) : AccessTokenBlacklist {

    override fun blacklistSession(sessionId: UUID) {
        // A session is blacklisted for the maximum remaining lifetime of any access token that
        // could have been issued before revocation. Redis removes the key automatically after it.
        redisTemplate.opsForValue().set(key(sessionId), "revoked", accessTokenTtl)
    }

    override fun isBlacklisted(claims: TokenClaims): Boolean =
        redisTemplate.hasKey(key(claims.sessionId))

    private fun key(sessionId: UUID) = "studymate:jwt:blacklist:session:$sessionId"
}
