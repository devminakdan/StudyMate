package cz.cvut.fit.studymate.iam.internal.exception

import java.util.UUID

internal data class UserNotFoundException(val id: UUID): RuntimeException("User with id $id not found")
