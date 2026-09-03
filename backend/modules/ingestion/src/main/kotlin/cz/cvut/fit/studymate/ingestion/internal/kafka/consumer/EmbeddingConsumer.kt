package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunkWithEmbeddingDto
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunksEmbeddedEvent
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextChunkedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import kotlinx.coroutines.runBlocking
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
internal class EmbeddingConsumer(
    private val objectMapper: ObjectMapper,
    private val processedEventsRepository: ProcessedEventsRepository,
    private val materialStatusUpdater: MaterialStatusUpdater,
    private val statusEventRelay: StatusEventRelay,
    private val embeddingClient: EmbeddingClient,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val kafkaTopics: KafkaTopicsProperties,
) {
    @KafkaListener(topics = ["\${studymate.kafka.topics.text-chunked}"], groupId = "ingestion-embedding")
    fun onMessage(payload: String, acknowledgment: Acknowledgment) {
        handle(objectMapper.readValue(payload, TextChunkedEvent::class.java))
        acknowledgment.acknowledge()
    }

    fun handle(event: TextChunkedEvent) {
        if (processedEventsRepository.exists(event.eventId)) return

        try {
            updateStatus(event, MaterialStatus.EMBEDDING)
            val embeddings = runBlocking { embeddingClient.embed(event.chunks.map { it.text }) }
            check(embeddings.size == event.chunks.size) {
                "Embedding provider returned ${embeddings.size} vectors for ${event.chunks.size} chunks"
            }
            val embeddedChunks = event.chunks.zip(embeddings) { chunk, embedding ->
                ChunkWithEmbeddingDto(chunk.chunkIndex, chunk.text, embedding)
            }
            kafkaTemplate.send(
                kafkaTopics.chunksEmbedded,
                event.materialId.toString(),
                ChunksEmbeddedEvent(
                    materialId = event.materialId,
                    courseId = event.courseId,
                    ownerId = event.ownerId,
                    chunks = embeddedChunks,
                ),
            ).get()
            processedEventsRepository.markProcessed(event.eventId)
        } catch (exception: Exception) {
            fail(event, exception)
        }
    }

    private fun updateStatus(event: TextChunkedEvent, status: MaterialStatus, error: String? = null) {
        materialStatusUpdater.updateStatus(event.materialId, status, error = error)
        statusEventRelay.relay(event.ownerId, StatusEvent(event.materialId, status, error))
    }

    private fun fail(event: TextChunkedEvent, exception: Exception) {
        logger?.error("Embedding failed for material ${event.materialId}", exception)
        runCatching { updateStatus(event, MaterialStatus.FAILED, exception.message) }
            .onFailure { logger?.error("Could not record embedding failure for material ${event.materialId}", it) }
        runCatching { processedEventsRepository.markProcessed(event.eventId) }
            .onFailure { logger?.error("Could not mark failed embedding event ${event.eventId} as processed", it) }
    }

    private companion object {
        val logger: Logger? = LoggerFactory.getLogger(EmbeddingConsumer::class.java)
    }
}
