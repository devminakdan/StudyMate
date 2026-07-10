package cz.cvut.fit.studymate.course.api

import java.time.OffsetDateTime
import java.util.UUID

data class Course(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val code: String?,
    val description: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)
