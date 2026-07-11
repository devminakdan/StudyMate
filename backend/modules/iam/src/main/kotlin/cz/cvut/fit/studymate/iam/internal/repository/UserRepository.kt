package cz.cvut.fit.studymate.iam.internal.repository

import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.generated.tables.references.USERS
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

internal data class UserWithHash(
    val user: User,
    val passwordHash: String,
)

@Repository
internal class UserRepository(
    private val dsl: DSLContext,
) {
    fun create(
        id: UUID,
        username: String,
        email: String,
        passwordHash: String,
        role: Role,
    ): User {
        val now = OffsetDateTime.now()
        dsl.insertInto(USERS)
            .set(USERS.ID, id)
            .set(USERS.EMAIL, email)
            .set(USERS.PASSWORD_HASH, passwordHash)
            .set(USERS.USERNAME, username)
            .set(USERS.ROLE, role.name)
            .set(USERS.CREATED_AT, now)
            .set(USERS.UPDATED_AT, now)
            .execute()

        return User(id, username, email, role, now, now)
    }

    fun existsByEmail(email: String): Boolean = dsl.fetchExists(
            dsl.selectOne().from(USERS).where(USERS.EMAIL.eq(email))
    )

    fun findById(id: UUID): User? {
        return dsl
            .select(USERS.asterisk())
            .from(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne()
            ?.toUser()
    }

    fun findByIds(ids: Collection<UUID>): Map<UUID, User> {
        if (ids.isEmpty()) return emptyMap()

        return dsl
            .select(USERS.asterisk())
            .from(USERS)
            .where(USERS.ID.`in`(ids))
            .fetch()
            .associate { record ->
                val userId = record.get(USERS.ID)!!
                userId to record.toUser()
            }
    }

    fun findByEmailWithHash(email: String): UserWithHash? {
        val record = dsl
            .select(USERS.asterisk())
            .from(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne() ?: return null

        return UserWithHash(
            user = record.toUser(),
            passwordHash = record.get(USERS.PASSWORD_HASH)!!,
        )
    }

    private fun org.jooq.Record.toUser(): User = User(
        id = this.get(USERS.ID)!!,
        email = this.get(USERS.EMAIL)!!,
        username = this.get(USERS.USERNAME)!!,
        role = Role.valueOf(this.get(USERS.ROLE)!!),
        createdAt = this.get(USERS.CREATED_AT)!!,
        updatedAt = this.get(USERS.UPDATED_AT)!!,
    )

}
