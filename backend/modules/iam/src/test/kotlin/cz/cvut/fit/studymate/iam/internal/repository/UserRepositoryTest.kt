package cz.cvut.fit.studymate.iam.internal.repository

import cz.cvut.fit.studymate.iam.api.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID

@Testcontainers
@SpringBootTest(
    classes = [UserRepositoryTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@Transactional
internal class UserRepositoryTest {

    @Configuration
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = [UserRepository::class])
    class TestConfig

    @Autowired
    lateinit var repository: UserRepository

    @Test
    fun `create persists a user with USER role and a generated id`() {
        val user = repository.create("alice", "alice@example.com", "hashed-password")

        assertThat(user.id).isNotNull()
        assertThat(user.username).isEqualTo("alice")
        assertThat(user.email).isEqualTo("alice@example.com")
        assertThat(user.role).isEqualTo(Role.USER)

        assertThat(repository.findById(user.id)).isEqualTo(user)
    }

    @Test
    fun `existsByEmail reflects persisted users`() {
        repository.create("bob", "bob@example.com", "hashed-password")

        assertThat(repository.existsByEmail("bob@example.com")).isTrue()
        assertThat(repository.existsByEmail("nobody@example.com")).isFalse()
    }

    @Test
    fun `findById returns null for an unknown id`() {
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    @Test
    fun `findByIds returns only the users that exist, keyed by id`() {
        val alice = repository.create("alice", "alice@example.com", "hash")
        val bob = repository.create("bob", "bob@example.com", "hash")
        val unknownId = UUID.randomUUID()

        val result = repository.findByIds(listOf(alice.id, bob.id, unknownId))

        assertThat(result).containsOnlyKeys(alice.id, bob.id)
        assertThat(result[alice.id]).isEqualTo(alice)
        assertThat(result[bob.id]).isEqualTo(bob)
    }

    @Test
    fun `findByIds returns an empty map for an empty input`() {
        assertThat(repository.findByIds(emptyList())).isEmpty()
    }

    @Test
    fun `findByEmailWithHash returns the stored password hash`() {
        val created = repository.create("carol", "carol@example.com", "super-secret-hash")

        val result = repository.findByEmailWithHash("carol@example.com")

        assertThat(result).isNotNull
        assertThat(result!!.user).isEqualTo(created)
        assertThat(result.passwordHash).isEqualTo("super-secret-hash")
    }

    @Test
    fun `findByEmailWithHash returns null for an unknown email`() {
        assertThat(repository.findByEmailWithHash("nobody@example.com")).isNull()
    }

    @Test
    fun `updateRole changes the role and returns the updated user`() {
        val created = repository.create("dave", "dave@example.com", "hash")

        val updated = repository.updateRole(created.id, Role.ADMIN)

        assertThat(updated).isNotNull
        assertThat(updated!!.role).isEqualTo(Role.ADMIN)
        assertThat(repository.findById(created.id)!!.role).isEqualTo(Role.ADMIN)
    }

    @Test
    fun `updateRole returns null for an unknown id`() {
        assertThat(repository.updateRole(UUID.randomUUID(), Role.ADMIN)).isNull()
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
            registry.add("spring.liquibase.change-log") { "classpath:db/changelog/iam-changelog.yml" }
        }
    }
}
