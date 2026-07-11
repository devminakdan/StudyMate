package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.dto.RegisterLoginResult
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
internal class AuthService(
    private val repository: UserRepository,
    private val jwtService: JwtService,
    private val passwordEncoder: PasswordEncoder,
) {
    fun register(username: String, email: String, password: String): RegisterLoginResult {
        val passwordHash = passwordEncoder.encode(password)
        val result = repository.create(username, email, passwordHash)
        val authUser = AuthenticatedUser(result.id, result.email, result.role)
        val tokens = jwtService.generateTokenPair(authUser)
        return RegisterLoginResult(result.id, result.email, result.username, tokens)
    }

    fun login(email: String, password: String): RegisterLoginResult {
        val userWithHash = repository.findByEmailWithHash(email)
            ?: throw BadCredentialsException("Invalid credentials")

        if (!passwordEncoder.matches(password, userWithHash.passwordHash)) {
            throw BadCredentialsException("Invalid credentials")
        }

        val user = userWithHash.user
        val authUser = AuthenticatedUser(user.id, user.email, user.role)
        val tokens = jwtService.generateTokenPair(authUser)
        return RegisterLoginResult(user.id, user.email, user.username, tokens)
    }
}
