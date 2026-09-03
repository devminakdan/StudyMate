package cz.cvut.fit.studymate.ingestion.internal.kafka.event

import java.time.OffsetDateTime
import java.util.UUID

data class ChunkWithEmbeddingDto(
    val chunkIndex: Int,
    val text: String,
    val embedding: FloatArray,
)

data class ChunksEmbeddedEvent(
    val eventId: UUID = UUID.randomUUID(),
    val materialId: UUID,
    val courseId: UUID,
    val ownerId: UUID,
    val chunks: List<ChunkWithEmbeddingDto>,
    val timestamp: OffsetDateTime = OffsetDateTime.now(),
)
