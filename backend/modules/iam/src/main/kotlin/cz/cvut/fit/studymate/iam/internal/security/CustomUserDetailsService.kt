package cz.cvut.fit.studymate.iam.internal.security

import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.security.core.userdetails.User as SpringUser

internal class UserPrincipal(
    val user: User,
    passwordHash: String,
) : SpringUser(user.email, passwordHash, setOf(SimpleGrantedAuthority(user.role.asAuthority())))

@Service
internal class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserPrincipal {
        val userWithHash = userRepository.findByEmailWithHash(email)
            ?: throw UsernameNotFoundException("User not found: $email")

        return UserPrincipal(userWithHash.user, userWithHash.passwordHash)
    }
}
