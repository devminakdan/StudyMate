package cz.cvut.fit.studymate.iam.api

import java.util.UUID

data class AuthenticatedUser(
    val id: UUID,
    val email: String,
    val role: Role
)
