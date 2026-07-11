package cz.cvut.fit.studymate.iam.internal.dto

import cz.cvut.fit.studymate.iam.internal.service.TokenPair
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.util.UUID

internal data class RegisterRequest(
    @field:NotBlank
    @field:Size(min = 2, max = 20)
    val username: String,

    @field:NotBlank
    @field:Size(min = 8, max = 30, message = "password must be 8-30 chars long")
    val password: String,

    @field:Email(message = "Must be a valid email address")
    @field:NotBlank
    val email: String
)

internal data class LoginRequest(
    @field:Email
    @field:NotBlank
    val email: String,

    @field:NotBlank
    val password: String
)

internal data class AuthResponse(
    val userId: UUID,
    val email: String,
    val username: String,
)

internal data class RegisterLoginResult(
    val userId: UUID,
    val email: String,
    val username: String,
    val tokens: TokenPair
)
