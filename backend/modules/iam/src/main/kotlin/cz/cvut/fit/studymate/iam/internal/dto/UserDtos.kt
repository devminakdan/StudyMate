package cz.cvut.fit.studymate.iam.internal.dto

import cz.cvut.fit.studymate.iam.api.User
import java.time.OffsetDateTime
import java.util.UUID

internal data class UserResponse(
    val id: UUID,
    val email: String,
    val username: String,
    val role: String,
    val createdAt: OffsetDateTime,
)

internal fun User.toResponse() = UserResponse(
    id = id,
    email = email,
    username = username,
    role = role.name,
    createdAt = createdAt,
)
