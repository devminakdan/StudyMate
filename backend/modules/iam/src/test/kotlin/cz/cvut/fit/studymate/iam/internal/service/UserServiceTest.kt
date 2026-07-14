package cz.cvut.fit.studymate.iam.internal.service

import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.api.User
import cz.cvut.fit.studymate.iam.internal.exception.UserNotFoundException
import cz.cvut.fit.studymate.iam.internal.repository.UserRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.UUID

internal class UserServiceTest {

    private val repository = mockk<UserRepository>()
    private val userService = UserService(repository)

    private fun user(
        id: UUID = UUID.randomUUID(),
        username: String = "alice",
        email: String = "alice@example.com",
        role: Role = Role.USER,
    ) = User(id, username, email, role, OffsetDateTime.now(), OffsetDateTime.now())

    @Test
    fun `findById returns the user when the repository finds one`() {
        val existing = user()
        every { repository.findById(existing.id) } returns existing

        assertThat(userService.findById(existing.id)).isEqualTo(existing)
    }

    @Test
    fun `findById throws UserNotFoundException instead of returning null when the repository finds nothing`() {
        val id = UUID.randomUUID()
        every { repository.findById(id) } returns null

        val ex = assertThrows<UserNotFoundException> { userService.findById(id) }
        assertThat(ex.id).isEqualTo(id)
    }

    @Test
    fun `findByIds returns a map keyed by id for the ids the repository finds, dropping unknown ids`() {
        val alice = user(username = "alice")
        val bob = user(username = "bob")
        val unknownId = UUID.randomUUID()

        every { repository.findByIds(listOf(alice.id, bob.id, unknownId)) } returns
            mapOf(alice.id to alice, bob.id to bob)

        val result = userService.findByIds(listOf(alice.id, bob.id, unknownId))

        assertThat(result).containsOnlyKeys(alice.id, bob.id)
        assertThat(result[alice.id]).isEqualTo(alice)
        assertThat(result[bob.id]).isEqualTo(bob)
    }

    @Test
    fun `findByIds returns an empty map for an empty input collection`() {
        every { repository.findByIds(emptyList()) } returns emptyMap()

        assertThat(userService.findByIds(emptyList())).isEmpty()
    }

    @Test
    fun `changeRole returns the updated user when the repository successfully updates the role`() {
        val existing = user(role = Role.USER)
        val updated = existing.copy(role = Role.ADMIN)
        every { repository.updateRole(existing.id, Role.ADMIN) } returns updated

        val result = userService.changeRole(existing.id, Role.ADMIN)

        assertThat(result.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `changeRole throws UserNotFoundException when the repository finds no matching user to update`() {
        val id = UUID.randomUUID()
        every { repository.updateRole(id, Role.ADMIN) } returns null

        val ex = assertThrows<UserNotFoundException> { userService.changeRole(id, Role.ADMIN) }
        assertThat(ex.id).isEqualTo(id)
    }
}
