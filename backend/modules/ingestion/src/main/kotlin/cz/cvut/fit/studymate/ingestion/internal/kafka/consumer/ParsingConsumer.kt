package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.MaterialUploadedEvent
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextExtractedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import cz.cvut.fit.studymate.storage.api.StorageRef
import cz.cvut.fit.studymate.storage.api.StorageService
import org.apache.tika.Tika
import org.slf4j.Logger
import org.springframework.kafka.support.Acknowledgment
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
internal class ParsingConsumer(
    private val objectMapper: ObjectMapper,
    private val processedEventsRepository: ProcessedEventsRepository,
    private val materialStatusUpdater: MaterialStatusUpdater,
    private val statusEventRelay: StatusEventRelay,
    private val storageService: StorageService,
    private val kafkaTemplate: KafkaTemplate<String, Any>,
    private val kafkaTopics: KafkaTopicsProperties,
    private val tika: Tika = Tika(),
) {
    @KafkaListener(topics = ["\${studymate.kafka.topics.material-uploaded}"], groupId = "ingestion-parsing")
    fun onMessage(payload: String, acknowledgment: Acknowledgment) {
        handle(objectMapper.readValue(payload, MaterialUploadedEvent::class.java))
        acknowledgment.acknowledge()
    }

    /** Visible to unit tests; Kafka always reaches this through [onMessage]. */
    fun handle(event: MaterialUploadedEvent) {
        if (processedEventsRepository.exists(event.eventId)) return

        try {
            updateStatus(event, MaterialStatus.PARSING)
            val text = storageService.retrieve(StorageRef(event.storagePath)).use { tika.parseToString(it) }
            kafkaTemplate.send(
                kafkaTopics.textExtracted,
                event.materialId.toString(),
                TextExtractedEvent(
                    materialId = event.materialId,
                    courseId = event.courseId,
                    ownerId = event.ownerId,
                    text = text,
                ),
            ).get()
            processedEventsRepository.markProcessed(event.eventId)
        } catch (exception: Exception) {
            fail(event, exception)
        }
    }

    private fun updateStatus(event: MaterialUploadedEvent, status: MaterialStatus, error: String? = null) {
        materialStatusUpdater.updateStatus(event.materialId, status, error = error)
        statusEventRelay.relay(event.ownerId, StatusEvent(event.materialId, status, error))
    }

    private fun fail(event: MaterialUploadedEvent, exception: Exception) {
        logger?.error("Parsing failed for material ${event.materialId}", exception)
        runCatching { updateStatus(event, MaterialStatus.FAILED, exception.message) }
            .onFailure { logger?.error("Could not record parsing failure for material ${event.materialId}", it) }
        runCatching { processedEventsRepository.markProcessed(event.eventId) }
            .onFailure { logger?.error("Could not mark failed parsing event ${event.eventId} as processed", it) }
    }

    private companion object {
        val logger: Logger? = LoggerFactory.getLogger(ParsingConsumer::class.java)
    }
}
