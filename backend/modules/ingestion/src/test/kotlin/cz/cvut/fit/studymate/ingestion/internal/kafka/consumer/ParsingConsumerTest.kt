package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.MaterialUploadedEvent
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextExtractedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import cz.cvut.fit.studymate.storage.api.StorageRef
import cz.cvut.fit.studymate.storage.api.StorageService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.apache.tika.Tika
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.io.ByteArrayInputStream
import java.util.concurrent.CompletableFuture

internal class ParsingConsumerTest {
    private val objectMapper = mockk<ObjectMapper>()
    private val processedEvents = mockk<ProcessedEventsRepository>()
    private val statusUpdater = mockk<MaterialStatusUpdater>()
    private val statusRelay = mockk<StatusEventRelay>()
    private val storage = mockk<StorageService>()
    private val kafkaTemplate = mockk<KafkaTemplate<String, Any>>()
    private val kafkaTopics = mockk<KafkaTopicsProperties>()
    private val consumer = ParsingConsumer(
        objectMapper,
        processedEvents,
        statusUpdater,
        statusRelay,
        storage,
        kafkaTemplate,
        kafkaTopics,
        Tika(),
    )

    @Test
    fun `extracts text, publishes the next event, and marks input processed`() {
        val event = event()
        val published = slot<Any>()
        every { processedEvents.exists(event.eventId) } returns false
        every { statusUpdater.updateStatus(any(), any(), any(), any()) } just Runs
        every { statusRelay.relay(any(), any()) } just Runs
        every { storage.retrieve(StorageRef(event.storagePath)) } returns ByteArrayInputStream("An extracted paragraph.".toByteArray())
        every { kafkaTemplate.send(any<String>(), any<String>(), capture(published)) } returns completedSend()
        every { kafkaTopics.textExtracted } returns "test.text-extracted"
        every { processedEvents.markProcessed(event.eventId) } just Runs

        consumer.handle(event)

        val downstream = published.captured as TextExtractedEvent
        assertEquals(event.materialId, downstream.materialId)
        assertEquals(event.courseId, downstream.courseId)
        assertEquals(event.ownerId, downstream.ownerId)
        assertEquals("An extracted paragraph.", downstream.text.trim())
        verify(exactly = 1) { statusUpdater.updateStatus(event.materialId, MaterialStatus.PARSING, null, null) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
    }

    @Test
    fun `duplicate event has no side effects`() {
        val event = event()
        every { processedEvents.exists(event.eventId) } returns true

        consumer.handle(event)

        verify(exactly = 0) { storage.retrieve(any()) }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 0) { statusUpdater.updateStatus(any(), any(), any(), any()) }
    }

    @Test
    fun `storage failure is converted to failed status and acknowledged by the caller`() {
        val event = event()
        every { processedEvents.exists(event.eventId) } returns false
        every { statusUpdater.updateStatus(any(), any(), any(), any()) } just Runs
        every { statusRelay.relay(any(), any()) } just Runs
        every { storage.retrieve(StorageRef(event.storagePath)) } throws IllegalStateException("File not found")
        every { processedEvents.markProcessed(event.eventId) } just Runs

        consumer.handle(event)

        verify(exactly = 1) { statusUpdater.updateStatus(event.materialId, MaterialStatus.FAILED, null, "File not found") }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
    }

    private fun event() = MaterialUploadedEvent(
        materialId = java.util.UUID.randomUUID(),
        courseId = java.util.UUID.randomUUID(),
        ownerId = java.util.UUID.randomUUID(),
        storagePath = "user/material.pdf",
        mimeType = "application/pdf",
    )

    private fun completedSend(): CompletableFuture<SendResult<String, Any>> =
        CompletableFuture.completedFuture(mockk())
}
