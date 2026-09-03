package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.chunking.SentenceAwareChunker
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextChunkedEvent
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextExtractedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
internal class ChunkingConsumer(
    private val objectMapper: ObjectMapper,
    private val processedEventsRepository: ProcessedEventsRepository,
    private val materialStatusUpdater: MaterialStatusUpdater,
    private val statusEventRelay: StatusEventRelay,
    private val chunker: SentenceAwareChunker,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val kafkaTopics: KafkaTopicsProperties,
) {
    @KafkaListener(topics = ["\${studymate.kafka.topics.text-extracted}"], groupId = "ingestion-chunking")
    fun onMessage(payload: String, acknowledgment: Acknowledgment) {
        handle(objectMapper.readValue(payload, TextExtractedEvent::class.java))
        acknowledgment.acknowledge()
    }

    fun handle(event: TextExtractedEvent) {
        if (processedEventsRepository.exists(event.eventId)) return

        try {
            updateStatus(event, MaterialStatus.CHUNKING)
            val chunks = chunker.chunk(event.text)
            require(chunks.isNotEmpty()) { "Extracted text does not contain any indexable content" }
            kafkaTemplate.send(
                kafkaTopics.textChunked,
                event.materialId.toString(),
                TextChunkedEvent(
                    materialId = event.materialId,
                    courseId = event.courseId,
                    ownerId = event.ownerId,
                    chunks = chunks,
                ),
            ).get()
            processedEventsRepository.markProcessed(event.eventId)
        } catch (exception: Exception) {
            fail(event, exception)
        }
    }

    private fun updateStatus(event: TextExtractedEvent, status: MaterialStatus, error: String? = null) {
        materialStatusUpdater.updateStatus(event.materialId, status, error = error)
        statusEventRelay.relay(event.ownerId, StatusEvent(event.materialId, status, error))
    }

    private fun fail(event: TextExtractedEvent, exception: Exception) {
        logger?.error("Chunking failed for material ${event.materialId}", exception)
        runCatching { updateStatus(event, MaterialStatus.FAILED, exception.message) }
            .onFailure { logger?.error("Could not record chunking failure for material ${event.materialId}", it) }
        runCatching { processedEventsRepository.markProcessed(event.eventId) }
            .onFailure { logger?.error("Could not mark failed chunking event ${event.eventId} as processed", it) }
    }

    private companion object {
        val logger: Logger? = LoggerFactory.getLogger(ChunkingConsumer::class.java)
    }
}
