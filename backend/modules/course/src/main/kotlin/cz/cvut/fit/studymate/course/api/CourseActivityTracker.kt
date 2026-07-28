package cz.cvut.fit.studymate.course.api

import java.util.UUID

interface CourseActivityTracker {
    fun markUsed(courseId: UUID)
}
