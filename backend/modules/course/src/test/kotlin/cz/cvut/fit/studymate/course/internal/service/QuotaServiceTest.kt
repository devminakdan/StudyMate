package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.internal.exception.QuotaExceededException
import cz.cvut.fit.studymate.course.internal.repository.CourseRepository
import cz.cvut.fit.studymate.course.internal.repository.MaterialRepository
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
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@SpringBootTest(
    classes = [QuotaServiceTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@Transactional
internal class QuotaServiceTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(QuotaService::class, CourseRepository::class, MaterialRepository::class)
    class TestConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    }

    @Autowired
    private lateinit var quotaService: QuotaService

    @Autowired
    private lateinit var courseRepository: CourseRepository

    @Autowired
    private lateinit var materialRepository: MaterialRepository

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

    private fun createTestCourse(ownerId: UUID): UUID =
        courseRepository.create(ownerId, "Test course ${UUID.randomUUID()}", null, null).id

    // ---- getUserStorageUsage ----

    @Test
    fun `getUserStorageUsage sums material sizes across all of that user's courses`() {
        val ownerId = createTestUser()
        val firstCourseId = createTestCourse(ownerId)
        val secondCourseId = createTestCourse(ownerId)
        materialRepository.create(firstCourseId, "a.pdf", "path/a.pdf", "application/pdf", 1000L)
        materialRepository.create(secondCourseId, "b.pdf", "path/b.pdf", "application/pdf", 2000L)

        assertThat(quotaService.getUserStorageUsage(ownerId)).isEqualTo(3000L)
    }

    @Test
    fun `getUserStorageUsage ignores other users' materials`() {
        val ownerId = createTestUser()
        val otherOwnerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val otherCourseId = createTestCourse(otherOwnerId)
        materialRepository.create(courseId, "a.pdf", "path/a.pdf", "application/pdf", 1000L)
        materialRepository.create(otherCourseId, "b.pdf", "path/b.pdf", "application/pdf", 5000L)

        assertThat(quotaService.getUserStorageUsage(ownerId)).isEqualTo(1000L)
    }

    @Test
    fun `getUserStorageUsage returns 0 for a user with no materials`() {
        val ownerId = createTestUser()

        assertThat(quotaService.getUserStorageUsage(ownerId)).isEqualTo(0L)
    }

    // ---- checkQuota ----

    @Test
    fun `checkQuota does not throw when under the limit`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        materialRepository.create(courseId, "a.pdf", "path/a.pdf", "application/pdf", 1000L)

        quotaService.checkQuota(ownerId, 2000L)
    }

    @Test
    fun `checkQuota throws QuotaExceededException when the limit would be exceeded`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        materialRepository.create(courseId, "a.pdf", "path/a.pdf", "application/pdf", StorageQuota.FREE_TIER_BYTES - 100)

        assertThrows<QuotaExceededException> {
            quotaService.checkQuota(ownerId, 200L)
        }
    }
}
