package cz.cvut.fit.studymate.ingestion.internal.kafka.event

import java.time.OffsetDateTime
import java.util.UUID

data class TextExtractedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val materialId: UUID,
    val courseId: UUID,
    val ownerId: UUID,
    val text: String,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
