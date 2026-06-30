package cz.cvut.fit.studymate.iam.api

import java.time.OffsetDateTime
import java.util.UUID

data class User(
    val id: UUID,
    val username: String,
    val email: String,
    val role: Role,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
