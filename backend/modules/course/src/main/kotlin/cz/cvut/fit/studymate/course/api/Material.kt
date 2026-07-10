package cz.cvut.fit.studymate.course.api

import java.time.OffsetDateTime
import java.util.UUID

data class Material(
    val id: UUID,
    val courseId: UUID,
    val originalFilename: String,
    val storagePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val status: MaterialStatus,
    val pageCount: Int?,
    val uploadedAt: OffsetDateTime
)
