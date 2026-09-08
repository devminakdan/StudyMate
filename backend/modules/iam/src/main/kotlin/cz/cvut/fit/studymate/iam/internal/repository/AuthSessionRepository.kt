package cz.cvut.fit.studymate.iam.internal.repository

import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

internal data class AuthSession(
    val id: UUID,
    val userId: UUID,
    val createdAt: OffsetDateTime,
    val lastUsedAt: OffsetDateTime,
    val expiresAt: OffsetDateTime,
    val revokedAt: OffsetDateTime?,
)

internal data class RefreshTokenWithSession(
    val id: UUID,
    val session: AuthSession,
    val expiresAt: OffsetDateTime,
    val usedAt: OffsetDateTime?,
    val revokedAt: OffsetDateTime?,
)

@Repository
internal class AuthSessionRepository(
    private val dsl: DSLContext,
) {
    fun createSession(
        userId: UUID,
        userAgent: String?,
        ipAddress: String?,
        expiresAt: OffsetDateTime,
    ): AuthSession {
        val now = OffsetDateTime.now()
        return dsl.insertInto(AUTH_SESSIONS)
            .set(SESSION_USER_ID, userId)
            .set(SESSION_CREATED_AT, now)
            .set(SESSION_LAST_USED_AT, now)
            .set(SESSION_EXPIRES_AT, expiresAt)
            .set(SESSION_USER_AGENT, userAgent)
            .set(SESSION_IP_ADDRESS, ipAddress)
            .returning()
            .fetchSingle(::toSession)
    }

    fun createRefreshToken(sessionId: UUID, tokenHash: String, expiresAt: OffsetDateTime) {
        dsl.insertInto(REFRESH_TOKENS)
            .set(TOKEN_SESSION_ID, sessionId)
            .set(TOKEN_HASH, tokenHash)
            .set(TOKEN_EXPIRES_AT, expiresAt)
            .execute()
    }

    /** Locks the token row. Call only inside the transaction that consumes or revokes it. */
    fun findRefreshTokenForUpdate(tokenHash: String): RefreshTokenWithSession? {
        val record = dsl.select(
            TOKEN_ID,
            TOKEN_EXPIRES_AT,
            TOKEN_USED_AT,
            TOKEN_REVOKED_AT,
            SESSION_ID,
            SESSION_USER_ID,
            SESSION_CREATED_AT,
            SESSION_LAST_USED_AT,
            SESSION_EXPIRES_AT,
            SESSION_REVOKED_AT,
        )
            .from(REFRESH_TOKENS)
            .join(AUTH_SESSIONS).on(TOKEN_SESSION_ID.eq(SESSION_ID))
            .where(TOKEN_HASH.eq(tokenHash))
            .forUpdate()
            .fetchOne() ?: return null

        return RefreshTokenWithSession(
            id = record.get(TOKEN_ID)!!,
            session = AuthSession(
                id = record.get(SESSION_ID)!!,
                userId = record.get(SESSION_USER_ID)!!,
                createdAt = record.get(SESSION_CREATED_AT)!!,
                lastUsedAt = record.get(SESSION_LAST_USED_AT)!!,
                expiresAt = record.get(SESSION_EXPIRES_AT)!!,
                revokedAt = record.get(SESSION_REVOKED_AT),
            ),
            expiresAt = record.get(TOKEN_EXPIRES_AT)!!,
            usedAt = record.get(TOKEN_USED_AT),
            revokedAt = record.get(TOKEN_REVOKED_AT),
        )
    }

    fun markRefreshTokenUsed(tokenId: UUID, now: OffsetDateTime): Boolean =
        dsl.update(REFRESH_TOKENS)
            .set(TOKEN_USED_AT, now)
            .where(TOKEN_ID.eq(tokenId))
            .and(TOKEN_USED_AT.isNull)
            .execute() == 1

    fun touchSession(sessionId: UUID, now: OffsetDateTime) {
        dsl.update(AUTH_SESSIONS)
            .set(SESSION_LAST_USED_AT, now)
            .where(SESSION_ID.eq(sessionId))
            .execute()
    }

    fun revokeSession(sessionId: UUID, now: OffsetDateTime) {
        dsl.update(AUTH_SESSIONS)
            .set(SESSION_REVOKED_AT, now)
            .where(SESSION_ID.eq(sessionId))
            .and(SESSION_REVOKED_AT.isNull)
            .execute()
        dsl.update(REFRESH_TOKENS)
            .set(TOKEN_REVOKED_AT, now)
            .where(TOKEN_SESSION_ID.eq(sessionId))
            .and(TOKEN_REVOKED_AT.isNull)
            .execute()
    }

    fun revokeAllActiveSessions(userId: UUID, now: OffsetDateTime): List<UUID> {
        val sessionIds = dsl.update(AUTH_SESSIONS)
            .set(SESSION_REVOKED_AT, now)
            .where(SESSION_USER_ID.eq(userId))
            .and(SESSION_REVOKED_AT.isNull)
            .returning(SESSION_ID)
            .fetch(SESSION_ID)

        if (sessionIds.isNotEmpty()) {
            dsl.update(REFRESH_TOKENS)
                .set(TOKEN_REVOKED_AT, now)
                .where(TOKEN_SESSION_ID.`in`(sessionIds))
                .and(TOKEN_REVOKED_AT.isNull)
                .execute()
        }
        return sessionIds
    }

    private fun toSession(record: Record): AuthSession = AuthSession(
        id = record.get(SESSION_ID)!!,
        userId = record.get(SESSION_USER_ID)!!,
        createdAt = record.get(SESSION_CREATED_AT)!!,
        lastUsedAt = record.get(SESSION_LAST_USED_AT)!!,
        expiresAt = record.get(SESSION_EXPIRES_AT)!!,
        revokedAt = record.get(SESSION_REVOKED_AT),
    )

    private companion object {
        val AUTH_SESSIONS = table(name("auth_sessions"))
        val REFRESH_TOKENS = table(name("refresh_tokens"))

        val SESSION_ID = field(name("auth_sessions", "id"), UUID::class.java)
        val SESSION_USER_ID = field(name("auth_sessions", "user_id"), UUID::class.java)
        val SESSION_CREATED_AT = field(name("auth_sessions", "created_at"), OffsetDateTime::class.java)
        val SESSION_LAST_USED_AT = field(name("auth_sessions", "last_used_at"), OffsetDateTime::class.java)
        val SESSION_EXPIRES_AT = field(name("auth_sessions", "expires_at"), OffsetDateTime::class.java)
        val SESSION_REVOKED_AT = field(name("auth_sessions", "revoked_at"), OffsetDateTime::class.java)
        val SESSION_USER_AGENT = field(name("auth_sessions", "user_agent"), String::class.java)
        val SESSION_IP_ADDRESS = field(name("auth_sessions", "ip_address"), String::class.java)

        val TOKEN_ID = field(name("refresh_tokens", "id"), UUID::class.java)
        val TOKEN_SESSION_ID = field(name("refresh_tokens", "session_id"), UUID::class.java)
        val TOKEN_HASH = field(name("refresh_tokens", "token_hash"), String::class.java)
        val TOKEN_EXPIRES_AT = field(name("refresh_tokens", "expires_at"), OffsetDateTime::class.java)
        val TOKEN_USED_AT = field(name("refresh_tokens", "used_at"), OffsetDateTime::class.java)
        val TOKEN_REVOKED_AT = field(name("refresh_tokens", "revoked_at"), OffsetDateTime::class.java)
    }
}
