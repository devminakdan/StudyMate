package cz.cvut.fit.studymate.retrieval.internal.exceptions

import java.util.UUID

class RetrievalCourseNotFoundException(courseId: UUID) : RuntimeException("Course not found: $courseId")
