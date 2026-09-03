package cz.cvut.fit.studymate.ingestion.internal.kafka.event

import java.time.OffsetDateTime
import java.util.UUID

data class ChunkDto(
    val chunkIndex: Int,
    val text: String,
)

data class TextChunkedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val materialId: UUID,
    val courseId: UUID,
    val ownerId: UUID,
    val chunks: List<ChunkDto>,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
