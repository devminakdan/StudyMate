package cz.cvut.fit.studymate.iam.internal.repository

import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.generated.tables.references.USERS
import cz.cvut.fit.studymate.iam.generated.tables.references.USER_ROLES
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime
import java.util.UUID

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
}
