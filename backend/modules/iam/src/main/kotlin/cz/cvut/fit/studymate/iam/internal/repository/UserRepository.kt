package cz.cvut.fit.studymate.iam.internal.repository

import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.generated.tables.references.USERS
import cz.cvut.fit.studymate.iam.generated.tables.references.USER_ROLES
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
        dsl.transaction { config ->
            val tx = config.dsl()

            tx.insertInto(USERS)
                .set(USERS.ID, id)
                .set(USERS.EMAIL, email)
                .set(USERS.PASSWORD_HASH, passwordHash)
                .set(USERS.USERNAME, username)
                .set(USERS.CREATED_AT, now)
                .set(USERS.UPDATED_AT, now)
                .execute()

            tx.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, id)
                .set(USER_ROLES.ROLE, role.name)
                .execute()
        }
        return User(id, username, email, role, now, now)
    }

    fun existsByEmail(email: String): Boolean = dsl.fetchExists(
            dsl.selectOne().from(USERS).where(USERS.EMAIL.eq(email))
    )

    fun findById(id: UUID): User? {
        val record = dsl
            .select(USERS.asterisk())
            .from(USERS)
            .where(USERS.ID.eq(id))
            .fetchOne() ?: return null

        return record.toUser(loadRole(id))
    }

    fun findByIds(ids: Collection<UUID>): Map<UUID, User> {
        if (ids.isEmpty()) return emptyMap()

        val users = dsl
            .select(USERS.asterisk())
            .from(USERS)
            .where(USERS.ID.`in`(ids))
            .fetch()

        val rolePerUser = dsl
            .select(USER_ROLES.USER_ID, USER_ROLES.ROLE)
            .from(USER_ROLES)
            .where(USER_ROLES.USER_ID.`in`(ids))
            .fetchGroups(USER_ROLES.USER_ID, USER_ROLES.ROLE)

        return users.associate { record ->
            val userId = record.get(USERS.ID)!!
            val role = Role.valueOf(rolePerUser[userId]!!.first()!!)
            userId to record.toUser(role)
        }
    }

    private fun loadRole(userId: UUID): Role {
        val roleStr = dsl
            .select(USER_ROLES.ROLE)
            .from(USER_ROLES)
            .where(USER_ROLES.USER_ID.eq(userId))
            .fetchOne(USER_ROLES.ROLE)
            ?: throw IllegalStateException("No role found for user $userId")
        return Role.valueOf(roleStr)
    }

    fun findByEmailWithHash(email: String): UserWithHash? {
        val record = dsl
            .select(USERS.asterisk())
            .from(USERS)
            .where(USERS.EMAIL.eq(email))
            .fetchOne() ?: return null

        val userId = record.get(USERS.ID)!!
        val role = loadRole(userId)

        return UserWithHash(
            user = User(
                id = userId,
                email = record.get(USERS.EMAIL)!!,
                username = record.get(USERS.USERNAME)!!,
                role = role,
                createdAt = record.get(USERS.CREATED_AT)!!,
                updatedAt = record.get(USERS.UPDATED_AT)!!,
            ),
            passwordHash = record.get(USERS.PASSWORD_HASH)!!,
        )
    }

    private fun org.jooq.Record.toUser(role: Role): User = User(
        id = this.get(USERS.ID)!!,
        email = this.get(USERS.EMAIL)!!,
        username = this.get(USERS.USERNAME)!!,
        role = role,
        createdAt = this.get(USERS.CREATED_AT)!!,
        updatedAt = this.get(USERS.UPDATED_AT)!!,
    )

}
