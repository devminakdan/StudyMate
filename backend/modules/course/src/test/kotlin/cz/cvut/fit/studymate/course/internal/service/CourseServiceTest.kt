package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseAlreadyExistsException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import cz.cvut.fit.studymate.course.internal.repository.CourseRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.dao.DataIntegrityViolationException
import java.time.OffsetDateTime
import java.util.UUID

internal class CourseServiceTest {

    private val repository = mockk<CourseRepository>()
    private val service = CourseService(repository)

    private fun course(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        name: String = "Programování a algoritmizace 2",
        code: String? = "BI-PA2.21",
        description: String? = null,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
    ) = Course(id, ownerId, name, code, description, createdAt, updatedAt)

    @Test
    fun `createCourse delegates to the repository and returns the created course`() {
        val course = course()
        every { repository.create(course.ownerId, course.name, course.code, course.description) } returns course

        val result = service.createCourse(course.ownerId, course.name, course.code, course.description)

        assertThat(result).isEqualTo(course)
    }

    @Test
    fun `createCourse throws CourseAlreadyExistsException when the repository reports a duplicate name`() {
        val course = course()
        every {
            repository.create(course.ownerId, course.name, course.code, course.description)
        } throws DataIntegrityViolationException("duplicate key")

        assertThrows<CourseAlreadyExistsException> {
            service.createCourse(course.ownerId, course.name, course.code, course.description)
        }
    }

    @Test
    fun `listCourses translates page and size into limit and offset when calling the repository`() {
        val ownerId = UUID.randomUUID()
        val courses = listOf(course(), course())
        every { repository.findByOwnerId(ownerId, limit = 10, offset = 20) } returns courses

        val result = service.listCourses(ownerId, page = 2, size = 10)

        assertThat(result).isEqualTo(courses)
    }

    @Test
    fun `countCourses delegates to the repository`() {
        val ownerId = UUID.randomUUID()
        every { repository.countByOwnerId(ownerId) } returns 5

        val result = service.countCourses(ownerId)

        assertThat(result).isEqualTo(5)
    }

    @Test
    fun `getCourse returns the course when it exists and belongs to the caller`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        val result = service.getCourse(course.id, course.ownerId)

        assertThat(result).isEqualTo(course)
    }

    @Test
    fun `getCourse throws CourseNotFoundException when no course exists with that id`() {
        val courseId = UUID.randomUUID()
        every { repository.findById(courseId) } returns null

        assertThrows<CourseNotFoundException> {
            service.getCourse(courseId, UUID.randomUUID())
        }
    }

    @Test
    fun `getCourse throws CourseAccessDeniedException when the course exists but belongs to someone else`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        assertThrows<CourseAccessDeniedException> {
            service.getCourse(course.id, UUID.randomUUID())
        }
    }

    @Test
    fun `updateCourse updates and returns the course when the caller is the owner`() {
        val existing = course()
        val updated = existing.copy(name = "New name")
        every { repository.findById(existing.id) } returns existing
        every { repository.update(existing.id, "New name", existing.code, existing.description) } returns updated

        val result = service.updateCourse(existing.id, existing.ownerId, "New name", existing.code, existing.description)

        assertThat(result).isEqualTo(updated)
    }

    @Test
    fun `updateCourse throws CourseNotFoundException without calling repository-update when no course exists`() {
        val courseId = UUID.randomUUID()
        every { repository.findById(courseId) } returns null

        assertThrows<CourseNotFoundException> {
            service.updateCourse(courseId, UUID.randomUUID(), "New name", null, null)
        }

        verify(exactly = 0) { repository.update(any(), any(), any(), any()) }
    }

    @Test
    fun `updateCourse throws CourseAccessDeniedException without calling repository-update when the caller isn't the owner`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        assertThrows<CourseAccessDeniedException> {
            service.updateCourse(course.id, UUID.randomUUID(), "New name", null, null)
        }

        verify(exactly = 0) { repository.update(any(), any(), any(), any()) }
    }

    @Test
    fun `updateCourse throws CourseAlreadyExistsException when the repository reports a duplicate name`() {
        val course = course()
        every { repository.findById(course.id) } returns course
        every {
            repository.update(course.id, "Taken name", course.code, course.description)
        } throws DataIntegrityViolationException("duplicate key")

        assertThrows<CourseAlreadyExistsException> {
            service.updateCourse(course.id, course.ownerId, "Taken name", course.code, course.description)
        }
    }

    @Test
    fun `deleteCourse deletes the course when the caller is the owner`() {
        val course = course()
        every { repository.findById(course.id) } returns course
        every { repository.delete(course.id) } just Runs

        service.deleteCourse(course.id, course.ownerId)

        verify(exactly = 1) { repository.delete(course.id) }
    }

    @Test
    fun `deleteCourse throws CourseNotFoundException without deleting when no course exists`() {
        val courseId = UUID.randomUUID()
        every { repository.findById(courseId) } returns null

        assertThrows<CourseNotFoundException> {
            service.deleteCourse(courseId, UUID.randomUUID())
        }

        verify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `deleteCourse throws CourseAccessDeniedException without deleting when the caller isn't the owner`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        assertThrows<CourseAccessDeniedException> {
            service.deleteCourse(course.id, UUID.randomUUID())
        }

        verify(exactly = 0) { repository.delete(any()) }
    }

    @Test
    fun `findCourseById delegates straight to the repository`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        val result = service.findCourseById(course.id)

        assertThat(result).isEqualTo(course)
    }

    @Test
    fun `isOwner returns true when the course exists and ownerId matches`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        val result = service.isOwner(course.id, course.ownerId)

        assertThat(result).isTrue()
    }

    @Test
    fun `isOwner returns false when the course exists but ownerId does not match`() {
        val course = course()
        every { repository.findById(course.id) } returns course

        val result = service.isOwner(course.id, UUID.randomUUID())

        assertThat(result).isFalse()
    }

    @Test
    fun `isOwner returns false when the course does not exist`() {
        val courseId = UUID.randomUUID()
        every { repository.findById(courseId) } returns null

        val result = service.isOwner(courseId, UUID.randomUUID())

        assertThat(result).isFalse()
    }
}
