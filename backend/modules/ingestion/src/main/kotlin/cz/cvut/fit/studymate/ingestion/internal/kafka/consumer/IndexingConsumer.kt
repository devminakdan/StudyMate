package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.indexing.ChunkIndexer
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunksEmbeddedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
internal class IndexingConsumer(
    private val objectMapper: ObjectMapper,
    private val processedEventsRepository: ProcessedEventsRepository,
    private val materialStatusUpdater: MaterialStatusUpdater,
    private val statusEventRelay: StatusEventRelay,
    private val chunkIndexer: ChunkIndexer,
    private val kafkaTopics: KafkaTopicsProperties,
) {
    @KafkaListener(topics = ["\${studymate.kafka.topics.chunks-embedded}"], groupId = "ingestion-indexing")
    fun onMessage(payload: String, acknowledgment: Acknowledgment) {
        handle(objectMapper.readValue(payload, ChunksEmbeddedEvent::class.java))
        acknowledgment.acknowledge()
    }

    fun handle(event: ChunksEmbeddedEvent) {
        if (processedEventsRepository.exists(event.eventId)) return

        try {
            updateStatus(event, MaterialStatus.INDEXING)
            chunkIndexer.indexChunks(event.materialId, event.courseId, event.ownerId, event.chunks)
            updateStatus(event, MaterialStatus.READY)
            processedEventsRepository.markProcessed(event.eventId)
        } catch (exception: Exception) {
            fail(event, exception)
        }
    }

    private fun updateStatus(event: ChunksEmbeddedEvent, status: MaterialStatus, error: String? = null) {
        materialStatusUpdater.updateStatus(event.materialId, status, error = error)
        statusEventRelay.relay(event.ownerId, StatusEvent(event.materialId, status, error))
    }

    private fun fail(event: ChunksEmbeddedEvent, exception: Exception) {
        logger?.error("Indexing failed for material ${event.materialId}", exception)
        runCatching { updateStatus(event, MaterialStatus.FAILED, exception.message) }
            .onFailure { logger?.error("Could not record indexing failure for material ${event.materialId}", it) }
        runCatching { processedEventsRepository.markProcessed(event.eventId) }
            .onFailure { logger?.error("Could not mark failed indexing event ${event.eventId} as processed", it) }
    }

    private companion object {
        val logger: Logger? = LoggerFactory.getLogger(IndexingConsumer::class.java)
    }
}
