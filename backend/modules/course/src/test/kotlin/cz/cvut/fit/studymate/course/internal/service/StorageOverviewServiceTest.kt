package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.internal.repository.CourseRepository
import cz.cvut.fit.studymate.course.internal.repository.MaterialRepository
import io.mockk.every
import io.mockk.mockk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

internal class StorageOverviewServiceTest {

    private val courseRepository = mockk<CourseRepository>()
    private val materialRepository = mockk<MaterialRepository>()
    private val service = StorageOverviewService(courseRepository, materialRepository)

    private fun course(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        name: String = "Course",
        lastUsedAt: OffsetDateTime? = null,
    ) = Course(id, ownerId, name, null, null, OffsetDateTime.now(), OffsetDateTime.now(), lastUsedAt)

    private fun material(courseId: UUID, sizeBytes: Long) = Material(
        UUID.randomUUID(), courseId, "file.pdf", "path", "application/pdf",
        sizeBytes, MaterialStatus.PENDING, null, OffsetDateTime.now(), null, null
    )

    @Test
    fun `getStorageOverview aggregates size and count per course`() {
        val userId = UUID.randomUUID()
        val firstCourse = course(ownerId = userId, name = "First")
        val secondCourse = course(ownerId = userId, name = "Second")
        every { courseRepository.findAllByOwnerIdOrderedByLastUsed(userId) } returns listOf(firstCourse, secondCourse)
        every { materialRepository.findAllByCourseId(firstCourse.id) } returns listOf(
            material(firstCourse.id, 1000L), material(firstCourse.id, 2000L)
        )
        every { materialRepository.findAllByCourseId(secondCourse.id) } returns listOf(material(secondCourse.id, 500L))

        val result = service.getStorageOverview(userId)

        assertThat(result.courses).hasSize(2)
        assertThat(result.courses[0].totalSizeBytes).isEqualTo(3000L)
        assertThat(result.courses[0].materialCount).isEqualTo(2)
        assertThat(result.courses[1].totalSizeBytes).isEqualTo(500L)
        assertThat(result.courses[1].materialCount).isEqualTo(1)
    }

    @Test
    fun `getStorageOverview totalUsedBytes equals the sum across all courses`() {
        val userId = UUID.randomUUID()
        val firstCourse = course(ownerId = userId)
        val secondCourse = course(ownerId = userId)
        every { courseRepository.findAllByOwnerIdOrderedByLastUsed(userId) } returns listOf(firstCourse, secondCourse)
        every { materialRepository.findAllByCourseId(firstCourse.id) } returns listOf(material(firstCourse.id, 1000L))
        every { materialRepository.findAllByCourseId(secondCourse.id) } returns listOf(material(secondCourse.id, 2500L))

        val result = service.getStorageOverview(userId)

        assertThat(result.totalUsedBytes).isEqualTo(3500L)
        assertThat(result.limitBytes).isEqualTo(StorageQuota.FREE_TIER_BYTES)
    }

    @Test
    fun `getStorageOverview preserves the never-used-first ordering from the repository`() {
        val userId = UUID.randomUUID()
        val neverUsed = course(ownerId = userId, lastUsedAt = null)
        val usedRecently = course(ownerId = userId, lastUsedAt = OffsetDateTime.now())
        every { courseRepository.findAllByOwnerIdOrderedByLastUsed(userId) } returns listOf(neverUsed, usedRecently)
        every { materialRepository.findAllByCourseId(any()) } returns emptyList()

        val result = service.getStorageOverview(userId)

        assertThat(result.courses.map { it.courseId }).containsExactly(neverUsed.id, usedRecently.id)
        assertThat(result.courses[0].lastUsedAt).isNull()
    }

    @Test
    fun `getStorageOverview returns an empty courses list and 0 totalUsedBytes for a user with no courses`() {
        val userId = UUID.randomUUID()
        every { courseRepository.findAllByOwnerIdOrderedByLastUsed(userId) } returns emptyList()

        val result = service.getStorageOverview(userId)

        assertThat(result.courses).isEmpty()
        assertThat(result.totalUsedBytes).isEqualTo(0L)
    }
}
