package cz.cvut.fit.studymate.course.api

import java.time.OffsetDateTime
import java.util.UUID

data class MaterialUploadedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val materialId: UUID,
    val courseId: UUID,
    val ownerId: UUID,
    val storagePath: String,
    val mimeType: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
