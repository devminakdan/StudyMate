package cz.cvut.fit.studymate.retrieval.internal.exceptions

import java.util.UUID

class RetrievalAccessDeniedException(courseId: UUID) : RuntimeException("Access to course $courseId is denied")
