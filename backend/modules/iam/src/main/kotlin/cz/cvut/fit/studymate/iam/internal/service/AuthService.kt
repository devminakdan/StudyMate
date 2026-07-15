package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.dto.RegisterLoginResult
import cz.cvut.fit.studymate.iam.internal.exception.InvalidTokenException
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import cz.cvut.fit.studymate.iam.internal.security.UserPrincipal
import io.jsonwebtoken.JwtException
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
internal class AuthService(
    private val repository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
    private val authenticationManager: AuthenticationManager,
) {
    fun register(username: String, email: String, password: String): RegisterLoginResult {
        val passwordHash = passwordEncoder.encode(password)
        val result = repository.create(username, email, passwordHash)
        val authUser = AuthenticatedUser(result.id, result.email, result.role)
        val tokens = jwtService.generateTokenPair(authUser)
        return RegisterLoginResult(result.id, result.email, result.username, tokens)
    }

    fun login(email: String, password: String): RegisterLoginResult {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(email, password)
        )
        val user = (authentication.principal as UserPrincipal).user

        val authUser = AuthenticatedUser(user.id, user.email, user.role)
        val tokens = jwtService.generateTokenPair(authUser)
        return RegisterLoginResult(user.id, user.email, user.username, tokens)
    }

    fun refresh(refreshToken: String): TokenPair {
        val claims = try {
            jwtService.parseAndValidate(refreshToken)
        } catch (_: JwtException) {
            throw InvalidTokenException("Invalid refresh token")
        }

        if (claims.type != TokenType.REFRESH) {
            throw InvalidTokenException("Token is not a refresh token")
        }

        val user = repository.findById(claims.userId)
            ?: throw InvalidTokenException("User no longer exists")

        val authUser = AuthenticatedUser(user.id, user.email, user.role)
        return jwtService.generateTokenPair(authUser)
    }
}
