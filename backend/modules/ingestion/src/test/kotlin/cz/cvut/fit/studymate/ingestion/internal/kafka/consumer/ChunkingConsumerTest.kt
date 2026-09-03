package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.chunking.SentenceAwareChunker
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextExtractedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import java.util.UUID

class ChunkingConsumerTest {
    private val objectMapper = mockk<ObjectMapper>()
    private val processedEvents = mockk<ProcessedEventsRepository>()
    private val statusUpdater = mockk<MaterialStatusUpdater>(relaxed = true)
    private val statusRelay = mockk<StatusEventRelay>(relaxed = true)
    private val kafkaTemplate = mockk<KafkaTemplate<String, Any>>()
    private val kafkaTopics = mockk<KafkaTopicsProperties>()
    private lateinit var consumer: ChunkingConsumer

    @BeforeEach
    fun setUp() {
        consumer = ChunkingConsumer(
            objectMapper = objectMapper,
            processedEventsRepository = processedEvents,
            materialStatusUpdater = statusUpdater,
            statusEventRelay = statusRelay,
            chunker = SentenceAwareChunker(),
            kafkaTemplate = kafkaTemplate,
            kafkaTopics = kafkaTopics,
        )
    }

    @Test
    fun `does nothing when event was already processed`() {
        val event = event(text = "A valid sentence.")
        every { processedEvents.exists(event.eventId) } returns true

        consumer.handle(event)

        verify(exactly = 1) { processedEvents.exists(event.eventId) }
        verify(exactly = 0) {
            statusUpdater.updateStatus(any(), any(), any(), any())
            statusRelay.relay(any(), any())
            kafkaTemplate.send(any<String>(), any<String>(), any())
            processedEvents.markProcessed(any())
        }
        confirmVerified(processedEvents, statusUpdater, statusRelay, kafkaTemplate)
    }

    @Test
    fun `marks blank extracted text as failed and prevents redelivery`() {
        val event = event(text = "  \n\t")
        every { processedEvents.exists(event.eventId) } returns false

        consumer.handle(event)

        verifyOrder {
            statusUpdater.updateStatus(event.materialId, MaterialStatus.CHUNKING, null, null)
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.CHUNKING))
            statusUpdater.updateStatus(
                event.materialId,
                MaterialStatus.FAILED,
                null,
                "Extracted text does not contain any indexable content",
            )
            statusRelay.relay(
                event.ownerId,
                StatusEvent(
                    event.materialId,
                    MaterialStatus.FAILED,
                    "Extracted text does not contain any indexable content",
                ),
            )
            processedEvents.markProcessed(event.eventId)
        }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any()) }
    }

    private fun event(text: String) = TextExtractedEvent(
        eventId = UUID.randomUUID(),
        materialId = UUID.randomUUID(),
        courseId = UUID.randomUUID(),
        ownerId = UUID.randomUUID(),
        text = text,
    )
}
