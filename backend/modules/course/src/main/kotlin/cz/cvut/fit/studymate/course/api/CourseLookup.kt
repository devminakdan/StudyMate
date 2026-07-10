package cz.cvut.fit.studymate.course.api

import java.util.UUID

interface CourseLookup {
    fun findCourseById(id: UUID): Course?
    fun isOwner(courseId: UUID, userId: UUID): Boolean
}
