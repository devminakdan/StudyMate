package cz.cvut.fit.studymate.course.internal.dto

import cz.cvut.fit.studymate.course.api.Course
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

internal data class CreateCourseRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 50)
    val code: String?,

    val description: String?,
)

internal data class UpdateCourseRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,

    @field:Size(max = 50)
    val code: String?,

    val description: String?,
)

internal data class CourseResponse(
    val id: UUID,
    val ownerId: UUID,
    val name: String,
    val code: String?,
    val description: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime,
)

internal fun Course.toResponse() = CourseResponse(
    id = id,
    ownerId = ownerId,
    name = name,
    code = code,
    description = description,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
