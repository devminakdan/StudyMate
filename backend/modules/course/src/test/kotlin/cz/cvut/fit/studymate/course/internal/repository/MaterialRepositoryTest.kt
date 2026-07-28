package cz.cvut.fit.studymate.course.internal.repository

import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.generated.tables.references.MATERIALS
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
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.OffsetDateTime
import java.util.UUID

@SpringBootTest(
    classes = [MaterialRepositoryTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@Transactional
internal class MaterialRepositoryTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(MaterialRepository::class, CourseRepository::class)
    class TestConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
    }

    @Autowired
    private lateinit var repository: MaterialRepository

    @Autowired
    private lateinit var dsl: DSLContext

    @Autowired
    private lateinit var courseRepository: CourseRepository

    private fun createTestUser(): UUID =
        dsl.insertInto(USERS)
            .set(USERS.EMAIL, "owner-${UUID.randomUUID()}@example.com")
            .set(USERS.PASSWORD_HASH, "irrelevant-for-this-test")
            .set(USERS.USERNAME, "owner")
            .set(USERS.ROLE, Role.USER.name)
            .returningResult(USERS.ID)
            .fetchOne()!!
            .value1()!!

    private fun createTestCourse(ownerId: UUID): UUID {
        return courseRepository.create(ownerId, "Test course ${UUID.randomUUID()}", null, null).id
    }

    private fun insertMaterialAt(courseId: UUID, uploadedAt: OffsetDateTime): Material =
        dsl.insertInto(MATERIALS)
            .set(MATERIALS.COURSE_ID, courseId)
            .set(MATERIALS.ORIGINAL_FILENAME, "test.pdf")
            .set(MATERIALS.STORAGE_PATH, "materials/$courseId/${UUID.randomUUID()}-test.pdf")
            .set(MATERIALS.MIME_TYPE, "application/pdf")
            .set(MATERIALS.SIZE_BYTES, 1024 * 1024L)
            .set(MATERIALS.UPLOADED_AT, uploadedAt)
            .returning()
            .fetchOneInto(Material::class.java)!!

    // ---- create ----

    @Test
    fun `create persists a material with a generated id and matching fields`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val storagePath = "materials/$courseId/${UUID.randomUUID()}-test.pdf"
        val sizeBytes = 1024 * 1024L

        val created = repository.create(courseId, "test.pdf", storagePath, "application/pdf", sizeBytes)

        assertThat(created.id).isNotNull()
        assertThat(created.courseId).isEqualTo(courseId)
        assertThat(created.storagePath).isEqualTo(storagePath)
        assertThat(created.originalFilename).isEqualTo("test.pdf")
        assertThat(created.mimeType).isEqualTo("application/pdf")
        assertThat(created.sizeBytes).isEqualTo(sizeBytes)

        assertThat(repository.findById(created.id)).isEqualTo(created)
    }

    @Test
    fun `create sets default status, pageCount, processedAt, and errorMessage for a new material`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val storagePath = "materials/$courseId/${UUID.randomUUID()}-test.pdf"
        val sizeBytes = 1024 * 1024L

        val created = repository.create(courseId, "test.pdf", storagePath, "application/pdf", sizeBytes)

        assertThat(created.status).isEqualTo(MaterialStatus.PENDING)
        assertThat(created.pageCount).isNull()
        assertThat(created.processedAt).isNull()
        assertThat(created.errorMessage).isNull()
    }

    @Test
    fun `create throws when courseId does not reference an existing course`() {
        val courseId = UUID.randomUUID()
        val storagePath = "materials/$courseId/${UUID.randomUUID()}-test.pdf"
        val sizeBytes = 1024 * 1024L

        assertThrows<DataIntegrityViolationException> {
            repository.create(courseId, "test.pdf", storagePath, "application/pdf", sizeBytes)
        }
    }

    // ---- findById ----

    @Test
    fun `findById returns the material when it exists`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val storagePath = "materials/$courseId/${UUID.randomUUID()}-test.pdf"
        val sizeBytes = 1024 * 1024L

        val created = repository.create(courseId, "test.pdf", storagePath, "application/pdf", sizeBytes)

        assertThat(repository.findById(created.id)).isEqualTo(created)
    }

    @Test
    fun `findById returns null for an unknown id`() {
        assertThat(repository.findById(UUID.randomUUID())).isNull()
    }

    // ---- findByCourseIdWithPagination ----

    @Test
    fun `findByCourseIdWithPagination returns only materials belonging to that course`() {
        val ownerId = createTestUser()
        val firstCourseId = createTestCourse(ownerId)
        val secondCourseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        val createdFirst = repository.create(firstCourseId, "test.pdf", "materials/$firstCourseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)
        val createdSecond = repository.create(secondCourseId, "test.pdf", "materials/$secondCourseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        val result = repository.findByCourseIdWithPagination(firstCourseId, 10, 0)

        assertThat(result).containsExactly(createdFirst)
    }

    @Test
    fun `findByCourseIdWithPagination respects limit`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)
        repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)
        repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        val result = repository.findByCourseIdWithPagination(courseId, 2, 0)

        assertThat(result.size).isEqualTo(2)
    }

    @Test
    fun `findByCourseIdWithPagination respects offset and orders newest first`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val now = OffsetDateTime.now()

        val oldest = insertMaterialAt(courseId, now.minusMinutes(2))
        val middle = insertMaterialAt(courseId, now.minusMinutes(1))
        val newest = insertMaterialAt(courseId, now)

        val resultFirst = repository.findByCourseIdWithPagination(courseId, 10, 0)
        assertThat(resultFirst).containsExactly(newest, middle, oldest)

        val resultSecond = repository.findByCourseIdWithPagination(courseId, 1, 1)
        assertThat(resultSecond).containsExactly(middle)
    }

    @Test
    fun `findByCourseIdWithPagination returns an empty list when the course has no materials`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)

        assertThat(repository.findByCourseIdWithPagination(courseId, limit = 10, offset = 0)).isEmpty()
    }

    // ---- findAllByCourseId ----

    @Test
    fun `findAllByCourseId returns all materials for a course, ordered newest first`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val now = OffsetDateTime.now()

        val oldest = insertMaterialAt(courseId, now.minusMinutes(2))
        val middle = insertMaterialAt(courseId, now.minusMinutes(1))
        val newest = insertMaterialAt(courseId, now)

        val result = repository.findAllByCourseId(courseId)

        assertThat(result).containsExactly(newest, middle, oldest)
    }

    @Test
    fun `findAllByCourseId returns an empty list when the course has no materials`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)

        assertThat(repository.findAllByCourseId(courseId)).isEmpty()
    }

    // ---- countByCourseId ----

    @Test
    fun `countByCourseId counts only that course's materials`() {
        val ownerId = createTestUser()
        val firstCourseId = createTestCourse(ownerId)
        val secondCourseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        repository.create(firstCourseId, "test.pdf", "materials/$firstCourseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)
        repository.create(firstCourseId, "test.pdf", "materials/$firstCourseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)
        repository.create(secondCourseId, "test.pdf", "materials/$secondCourseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        assertThat(repository.countByCourseId(firstCourseId)).isEqualTo(2)
        assertThat(repository.countByCourseId(secondCourseId)).isEqualTo(1)
    }

    @Test
    fun `countByCourseId returns 0 for a course with no materials`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)

        assertThat(repository.countByCourseId(courseId)).isEqualTo(0)
    }

    // ---- updateStatus ----

    @Test
    fun `updateStatus sets processedAt when status is READY`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        val material = repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        val updated = repository.updateStatus(material.id, MaterialStatus.READY, pageCount = 5)

        assertThat(updated!!.status).isEqualTo(MaterialStatus.READY)
        assertThat(updated.processedAt).isNotNull()
        assertThat(updated.pageCount).isEqualTo(5)
    }

    @Test
    fun `updateStatus sets processedAt when status is FAILED`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        val material = repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        val updated = repository.updateStatus(material.id, MaterialStatus.FAILED, error = "Corrupted file")

        assertThat(updated!!.status).isEqualTo(MaterialStatus.FAILED)
        assertThat(updated.processedAt).isNotNull()
        assertThat(updated.errorMessage).isEqualTo("Corrupted file")
    }

    @Test
    fun `updateStatus leaves processedAt null for a non-terminal status`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        val material = repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        val updated = repository.updateStatus(material.id, MaterialStatus.PARSING)

        assertThat(updated!!.status).isEqualTo(MaterialStatus.PARSING)
        assertThat(updated.processedAt).isNull()
    }

    @Test
    fun `updateStatus returns null when no material exists with that id`() {
        assertThat(repository.updateStatus(UUID.randomUUID(), MaterialStatus.PARSING)).isNull()
    }

    // ---- delete ----

    @Test
    fun `delete removes the material`() {
        val ownerId = createTestUser()
        val courseId = createTestCourse(ownerId)
        val sizeBytes = 1024 * 1024L

        val created = repository.create(courseId, "test.pdf", "materials/$courseId/${UUID.randomUUID()}-test.pdf", "application/pdf", sizeBytes)

        repository.delete(created.id)

        assertThat(repository.findById(created.id)).isNull()
    }

    @Test
    fun `delete does not throw for an unknown id`() {
        repository.delete(UUID.randomUUID())
    }
}
