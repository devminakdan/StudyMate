package cz.cvut.fit.studymate.iam.internal.security

import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service
import org.springframework.security.core.userdetails.User as SpringUser

@Service
internal class CustomUserDetailsService(
    private val userRepository: UserRepository,
) : UserDetailsService {

    override fun loadUserByUsername(email: String): UserDetails? {
        val userWithHash = userRepository.findByEmailWithHash(email)
            ?: throw UsernameNotFoundException("User not found: $email")

        return SpringUser.builder()
            .username(userWithHash.user.email)
            .password(userWithHash.passwordHash)
            .authorities(userWithHash.user.role.asAuthority())
            .build()
    }

}
