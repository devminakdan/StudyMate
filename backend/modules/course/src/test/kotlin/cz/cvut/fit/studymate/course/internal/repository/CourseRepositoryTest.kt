package cz.cvut.fit.studymate.course.internal.repository

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.generated.tables.references.COURSES
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.generated.tables.references.USERS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(
    classes = [CourseRepositoryTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@Transactional
internal class CourseRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @ComponentScan(basePackageClasses = [CourseRepository::class])
    class TestConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    }

    @Autowired
    private lateinit var repository: CourseRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private fun createTestUser(): UUID =
        dsl.insertInto(USERS)
            .set(USERS.EMAIL, "owner-${UUID.randomUUID()}@example.com")
            .set(USERS.PASSWORD_HASH, "irrelevant-for-this-test")
            .set(USERS.USERNAME, "owner")
            .set(USERS.ROLE, Role.USER.name)
            .returningResult(USERS.ID)
            .fetchOne()!!
            .value1()!!
    
    // now() is frozen for the whole @Transactional test, so create() can't produce distinct
    // timestamps here — insert directly with an explicit created_at instead.
    private fun insertCourseAt(ownerId: UUID, name: String, createdAt: OffsetDateTime): Course =
        dsl.insertInto(COURSES)
            .set(COURSES.OWNER_ID, ownerId)
            .set(COURSES.NAME, name)
            .set(COURSES.CREATED_AT, createdAt)
            .set(COURSES.UPDATED_AT, createdAt)
            .returning()
            .fetchOneInto(Course::class.java)!!

    // ---- create ----

    @Test
    fun `create persists a course with a generated id and matching fields`() {
        val ownerId = createTestUser()

        val created = repository.create(ownerId, "Programování a algoritmizace 2", "BI-PA2.21", "Intro to algorithms")

        assertThat(created.id).isNotNull()
        assertThat(created.ownerId).isEqualTo(ownerId)
        assertThat(created.name).isEqualTo("Programování a algoritmizace 2")
        assertThat(created.code).isEqualTo("BI-PA2.21")
        assertThat(created.description).isEqualTo("Intro to algorithms")
        assertThat(repository.findById(created.id)).isEqualTo(created)
    }

    @Test
    fun `create persists null code and null description`() {
        val ownerId = createTestUser()

        val created = repository.create(ownerId, "Elective", null, null)

        assertThat(created.code).isNull()
        assertThat(created.description).isNull()
    }

    @Test
    fun `create throws when the owner already has a course with this name`() {
        val ownerId = createTestUser()
        repository.create(ownerId, "Duplicate name", "A", null)

        assertThrows<DataIntegrityViolationException> {
            repository.create(ownerId, "Duplicate name", "B", null)
        }
    }

    @Test
    fun `create allows the same name for two different owners`() {
        val firstOwner = createTestUser()
        val secondOwner = createTestUser()
        repository.create(firstOwner, "Shared name", null, null)

        val created = repository.create(secondOwner, "Shared name", null, null)

        assertThat(created.ownerId).isEqualTo(secondOwner)
    }

    @Test
    fun `create throws when ownerId does not reference an existing user`() {
        assertThrows<DataIntegrityViolationException> {
            repository.create(UUID.randomUUID(), "Orphan course", null, null)
        }
    }

    // ---- findById ----

    @Test
    fun `findById returns the course when it exists`() {
        val ownerId = createTestUser()
        val created = repository.create(ownerId, "Findable", null, null)

        assertThat(repository.findById(created.id)).isEqualTo(created)
    }

    @Test
    fun `findById returns null for an unknown id`() {
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    // ---- findByOwnerId ----

    @Test
    fun `findByOwnerId returns only courses belonging to that owner`() {
        val ownerId = createTestUser()
        val otherOwnerId = createTestUser()
        val mine = repository.create(ownerId, "Mine", null, null)
        repository.create(otherOwnerId, "Not mine", null, null)

        val result = repository.findByOwnerId(ownerId, limit = 10, offset = 0)

        assertThat(result).containsExactly(mine)
    }

    @Test
    fun `findByOwnerId respects limit`() {
        val ownerId = createTestUser()
        repository.create(ownerId, "First", null, null)
        repository.create(ownerId, "Second", null, null)
        repository.create(ownerId, "Third", null, null)

        val result = repository.findByOwnerId(ownerId, limit = 2, offset = 0)

        assertThat(result).hasSize(2)
    }

    @Test
    fun `findByOwnerId respects offset and orders newest first`() {
        val ownerId = createTestUser()
        val now = OffsetDateTime.now()
        val oldest = insertCourseAt(ownerId, "Oldest", now.minusMinutes(2))
        val middle = insertCourseAt(ownerId, "Middle", now.minusMinutes(1))
        val newest = insertCourseAt(ownerId, "Newest", now)

        assertThat(repository.findByOwnerId(ownerId, limit = 10, offset = 0))
            .containsExactly(newest, middle, oldest)
        assertThat(repository.findByOwnerId(ownerId, limit = 1, offset = 1))
            .containsExactly(middle)
    }

    @Test
    fun `findByOwnerId returns an empty list when the owner has no courses`() {
        val ownerId = createTestUser()

        assertThat(repository.findByOwnerId(ownerId, limit = 10, offset = 0)).isEmpty()
    }

    // ---- countByOwnerId ----

    @Test
    fun `countByOwnerId counts only that owner's courses`() {
        val ownerId = createTestUser()
        val otherOwnerId = createTestUser()
        repository.create(ownerId, "First", null, null)
        repository.create(ownerId, "Second", null, null)
        repository.create(otherOwnerId, "Not counted", null, null)

        assertThat(repository.countByOwnerId(ownerId)).isEqualTo(2)
    }

    @Test
    fun `countByOwnerId returns 0 for an owner with no courses`() {
        val ownerId = createTestUser()

        assertThat(repository.countByOwnerId(ownerId)).isEqualTo(0)
    }

    // ---- update ----

    @Test
    fun `update changes fields and returns the updated course`() {
        val ownerId = createTestUser()
        val created = repository.create(ownerId, "Old name", "OLD", "Old description")

        val updated = repository.update(created.id, "New name", "NEW", "New description")

        assertThat(updated).isNotNull()
        assertThat(updated!!.name).isEqualTo("New name")
        assertThat(updated.code).isEqualTo("NEW")
        assertThat(updated.description).isEqualTo("New description")
    }

    @Test
    fun `update is visible through a subsequent findById`() {
        val ownerId = createTestUser()
        val created = repository.create(ownerId, "Old name", null, null)

        repository.update(created.id, "New name", null, null)

        assertThat(repository.findById(created.id)!!.name).isEqualTo("New name")
    }

    @Test
    fun `update bumps updatedAt`() {
        val ownerId = createTestUser()
        val created = repository.create(ownerId, "Name", null, null)

        val updated = repository.update(created.id, "Name", null, null)

        assertThat(updated!!.updatedAt).isAfter(created.updatedAt)
    }

    @Test
    fun `update returns null when no course exists with that id`() {
        assertThat(repository.update(UUID.randomUUID(), "Doesn't matter", null, null)).isNull()
    }

    @Test
    fun `update throws when renaming to a name the owner already has on another course`() {
        val ownerId = createTestUser()
        repository.create(ownerId, "Taken", null, null)
        val other = repository.create(ownerId, "Other", null, null)

        assertThrows<DataIntegrityViolationException> {
            repository.update(other.id, "Taken", null, null)
        }
    }

    // ---- delete ----

    @Test
    fun `delete removes the course`() {
        val ownerId = createTestUser()
        val created = repository.create(ownerId, "To delete", null, null)

        repository.delete(created.id)

        assertThat(repository.findById(created.id)).isNull()
    }

    @Test
    fun `delete does not throw for an unknown id`() {
        repository.delete(UUID.randomUUID())
    }
}
