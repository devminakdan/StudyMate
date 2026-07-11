package cz.cvut.fit.studymate.iam.internal.controller

import cz.cvut.fit.studymate.iam.internal.dto.AuthResponse
import cz.cvut.fit.studymate.iam.internal.dto.LoginRequest
import cz.cvut.fit.studymate.iam.internal.dto.RegisterRequest
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.security.JwtCookies
import cz.cvut.fit.studymate.iam.internal.service.AuthService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/auth")
internal class AuthController(
    private val authService: AuthService,
    private val jwtCookies: JwtCookies
) {
    @PostMapping("/register")
    fun register(
        @Valid @RequestBody req: RegisterRequest,
        response: HttpServletResponse
    ): ResponseEntity<AuthResponse> {
        val result = authService.register(req.username, req.email, req.password)

        jwtCookies.setTokens(response, result.tokens.accessToken, result.tokens.refreshToken)

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(AuthResponse(result.userId, result.email, result.username))
    }

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody req: LoginRequest,
        response: HttpServletResponse
    ): AuthResponse {
        val result = authService.login(req.email, req.password)
        jwtCookies.setTokens(response, result.tokens.accessToken, result.tokens.refreshToken)

        return AuthResponse(result.userId, result.email, result.username)
    }

    @PostMapping("/refresh")
    fun refresh(
        request: HttpServletRequest,
        response: HttpServletResponse,
    ): ResponseEntity<Void> {
        val refreshToken = jwtCookies.extractRefreshToken(request)
            ?: throw InvalidTokenException("No refresh token provided")

        val newTokens = authService.refresh(refreshToken)
        jwtCookies.setTokens(response, newTokens.accessToken, newTokens.refreshToken)

        return ResponseEntity.noContent().build()
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): ResponseEntity<Void> {
        jwtCookies.clearTokens(response)
        return ResponseEntity.noContent().build()
    }
}
