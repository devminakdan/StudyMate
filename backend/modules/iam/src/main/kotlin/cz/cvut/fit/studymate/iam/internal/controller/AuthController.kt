package cz.cvut.fit.studymate.iam.internal.controller

import cz.cvut.fit.studymate.iam.internal.dto.AuthResponse
import cz.cvut.fit.studymate.iam.internal.dto.LoginRequest
import cz.cvut.fit.studymate.iam.internal.dto.RegisterRequest
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.security.JwtCookies
import cz.cvut.fit.studymate.iam.internal.service.AuthService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Auth", description = "Registration, login, token refresh and logout")
@RestController
@RequestMapping("/api/v1/auth")
internal class AuthController(
    private val authService: AuthService,
    private val jwtCookies: JwtCookies
) {
    @Operation(
        summary = "Register a new user",
        description = "Creates an account and issues an access/refresh token pair as httpOnly cookies. Public — no authentication required.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "201", description = "User created", content = [Content(schema = Schema(implementation = AuthResponse::class))]),
        ApiResponse(responseCode = "400", description = "Validation failed: blank/too short/too long username or password, or invalid email", content = [Content()]),
    )
    @PostMapping("/register")
    @ResponseStatus(value = HttpStatus.CREATED)
    fun register(
        @Valid @RequestBody req: RegisterRequest,
        response: HttpServletResponse
    ): AuthResponse {
        val result = authService.register(req.username, req.email, req.password)

        jwtCookies.setTokens(response, result.tokens.accessToken, result.tokens.refreshToken)

        return AuthResponse(result.userId, result.email, result.username)
    }

    @Operation(
        summary = "Log in",
        description = "Verifies credentials and issues a new access/refresh token pair as httpOnly cookies. Public — no authentication required.",
    )
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Logged in", content = [Content(schema = Schema(implementation = AuthResponse::class))]),
        ApiResponse(responseCode = "400", description = "Validation failed: blank/invalid email, or blank password", content = [Content()]),
        ApiResponse(responseCode = "401", description = "Invalid credentials — same message whether the email is unknown or the password is wrong", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @PostMapping("/login")
    fun login(
        @Valid @RequestBody req: LoginRequest,
        response: HttpServletResponse
    ): AuthResponse {
        val result = authService.login(req.email, req.password)
        jwtCookies.setTokens(response, result.tokens.accessToken, result.tokens.refreshToken)

        return AuthResponse(result.userId, result.email, result.username)
    }

    @Operation(
        summary = "Refresh the token pair",
        description = "Rotates the access/refresh token pair using the refresh_token cookie. Public endpoint — the refresh token itself is the credential, not the usual access-token auth.",
    )
    @SecurityRequirement(name = "refreshTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Tokens rotated, new cookies set"),
        ApiResponse(
            responseCode = "401",
            description = "No refresh_token cookie present, the token is malformed/expired, it's an access token used where a refresh token is expected, or the user it references no longer exists",
            content = [Content(schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping("/refresh")
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ) {
        val refreshToken = jwtCookies.extractRefreshToken(request)
            ?: throw InvalidTokenException("No refresh token provided")

        val newTokens = authService.refresh(refreshToken)
        jwtCookies.setTokens(response, newTokens.accessToken, newTokens.refreshToken)
    }

    @Operation(
        summary = "Log out",
        description = "Clears the access/refresh token cookies. Requires a valid access_token cookie, unlike register/login/refresh.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Cookies cleared"),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or it's missing/invalid/unparseable", content = [Content()]),
    )
    @PostMapping("/logout")
    @ResponseStatus(value = HttpStatus.NO_CONTENT)
    fun logout(response: HttpServletResponse){
        jwtCookies.clearTokens(response)
    }
}
