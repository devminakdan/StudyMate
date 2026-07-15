package cz.cvut.fit.studymate.iam.internal.security

import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import cz.cvut.fit.studymate.iam.internal.repository.UserWithHash
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.security.core.userdetails.UsernameNotFoundException
import java.time.OffsetDateTime
import java.util.UUID

internal class CustomUserDetailsServiceTest {

    private val repository = mockk<UserRepository>()
    private val service = CustomUserDetailsService(repository)

    @Test
    fun `loadUserByUsername returns a UserPrincipal wrapping the domain user, with a ROLE_ authority`() {
        val existing = User(UUID.randomUUID(), "alice", "alice@example.com", Role.ADMIN, OffsetDateTime.now(), OffsetDateTime.now())
        every { repository.findByEmailWithHash("alice@example.com") } returns UserWithHash(existing, "hashed-pw")

        val principal = service.loadUserByUsername("alice@example.com")

        assertThat(principal.user).isEqualTo(existing)
        assertThat(principal.username).isEqualTo("alice@example.com")
        assertThat(principal.password).isEqualTo("hashed-pw")
        assertThat(principal.authorities.map { it.authority }).containsExactly("ROLE_ADMIN")
    }

    @Test
    fun `loadUserByUsername throws UsernameNotFoundException when no user has that email`() {
        every { repository.findByEmailWithHash("nobody@example.com") } returns null

        assertThrows<UsernameNotFoundException> {
            service.loadUserByUsername("nobody@example.com")
        }
    }
}
