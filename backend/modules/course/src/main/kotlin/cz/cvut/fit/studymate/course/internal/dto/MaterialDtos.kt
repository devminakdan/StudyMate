package cz.cvut.fit.studymate.course.internal.dto

import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import java.time.OffsetDateTime
import java.util.UUID

internal data class MaterialResponse(
    val id: UUID,
    val courseId: UUID,
    val originalFilename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: MaterialStatus,
    val pageCount: Int?,
    val uploadedAt: OffsetDateTime,
    val processedAt: OffsetDateTime?,
    val errorMessage: String?,
)

internal fun Material.toResponse() = MaterialResponse(
    id = id,
    courseId = courseId,
    originalFilename = originalFilename,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    status = status,
    pageCount = pageCount,
    uploadedAt = uploadedAt,
    processedAt = processedAt,
    errorMessage = errorMessage,
)
