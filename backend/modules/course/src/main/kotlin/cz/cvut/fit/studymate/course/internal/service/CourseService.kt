package cz.cvut.fit.studymate.course.internal.service

import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.api.CourseLookup
import cz.cvut.fit.studymate.course.internal.exception.CourseAccessDeniedException
import cz.cvut.fit.studymate.course.internal.exception.CourseNotFoundException
import cz.cvut.fit.studymate.course.internal.repository.CourseRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class CourseService(
    private val courseRepository: CourseRepository
) : CourseLookup {
    private fun requireOwnership(courseId: UUID, userId: UUID): Course {
        val course = courseRepository.findById(courseId)
            ?: throw CourseNotFoundException(courseId)
        if (course.ownerId != userId) {
            throw CourseAccessDeniedException("You don't own course $courseId")
        }
        return course
    }

    fun createCourse(ownerId: UUID, name: String, code: String?, description: String?): Course =
        courseRepository.create(ownerId, name, code, description)

    fun listCourses(ownerId: UUID) = courseRepository.findByOwnerId(ownerId)

    fun getCourse(courseId: UUID, userId: UUID) = requireOwnership(courseId, userId)

    fun updateCourse(courseId: UUID, userId: UUID, name: String, code: String?, description: String?): Course {
        requireOwnership(courseId, userId)
        return courseRepository.update(courseId, name, code, description)!!
    }

    fun deleteCourse(courseId: UUID, userId: UUID) {
        requireOwnership(courseId, userId)
        courseRepository.delete(courseId)
    }

    override fun findCourseById(id: UUID) = courseRepository.findById(id)

    override fun isOwner(courseId: UUID, userId: UUID) = courseRepository.findById(courseId)?.ownerId == userId
}
