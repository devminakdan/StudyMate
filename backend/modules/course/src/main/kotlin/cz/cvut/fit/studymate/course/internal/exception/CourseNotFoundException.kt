package cz.cvut.fit.studymate.course.internal.exception

import java.util.UUID

class CourseNotFoundException(id: UUID) : RuntimeException("Course not found: $id")
