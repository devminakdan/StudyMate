package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import cz.cvut.fit.studymate.course.api.KafkaTopicsProperties
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunkDto
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunksEmbeddedEvent
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.TextChunkedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class EmbeddingConsumerTest {
    private val objectMapper = mockk<ObjectMapper>()
    private val processedEvents = mockk<ProcessedEventsRepository>()
    private val statusUpdater = mockk<MaterialStatusUpdater>(relaxed = true)
    private val statusRelay = mockk<StatusEventRelay>(relaxed = true)
    private val embeddingClient = mockk<EmbeddingClient>()
    private val kafkaTemplate = mockk<KafkaTemplate<String, Any>>()
    private val kafkaTopics = mockk<KafkaTopicsProperties>()
    private val chunksEmbeddedTopic = "test.chunks-embedded"
    private lateinit var consumer: EmbeddingConsumer

    @BeforeEach
    fun setUp() {
        every { processedEvents.markProcessed(any()) } just Runs
        every { kafkaTopics.chunksEmbedded } returns chunksEmbeddedTopic
        consumer = EmbeddingConsumer(
            objectMapper = objectMapper,
            processedEventsRepository = processedEvents,
            materialStatusUpdater = statusUpdater,
            statusEventRelay = statusRelay,
            embeddingClient = embeddingClient,
            kafkaTemplate = kafkaTemplate,
            kafkaTopics = kafkaTopics,
        )
    }

    @Test
    fun `embeds 65 chunks in ordered batches and publishes one complete event`() {
        val event = event(65)
        val firstBatch = event.chunks.take(32).map { it.text }
        val secondBatch = event.chunks.drop(32).take(32).map { it.text }
        val lastBatch = listOf(event.chunks.last().text)
        val embeddings = List(65) { position -> floatArrayOf(position.toFloat(), position + 0.25f) }
        val published = slot<Any>()
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(firstBatch) } returns embeddings.take(32)
        coEvery { embeddingClient.embed(secondBatch) } returns embeddings.drop(32).take(32)
        coEvery { embeddingClient.embed(lastBatch) } returns listOf(embeddings.last())
        every {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), capture(published))
        } returns completedSend()

        consumer.handle(event)

        coVerifyOrder {
            statusUpdater.updateStatus(event.materialId, MaterialStatus.EMBEDDING, null, null)
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.EMBEDDING))
            embeddingClient.embed(firstBatch)
            embeddingClient.embed(secondBatch)
            embeddingClient.embed(lastBatch)
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), any<Any>())
            processedEvents.markProcessed(event.eventId)
        }
        coVerify(exactly = 3) { embeddingClient.embed(any()) }
        verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
        verify(exactly = 0) { statusUpdater.updateStatus(any(), MaterialStatus.FAILED, any(), any()) }

        val downstream = published.captured as ChunksEmbeddedEvent
        assertEquals(event.materialId, downstream.materialId)
        assertEquals(event.courseId, downstream.courseId)
        assertEquals(event.ownerId, downstream.ownerId)
        assertEquals(65, downstream.chunks.size)
        for (position in event.chunks.indices) {
            val original = event.chunks[position]
            val embedded = downstream.chunks[position]
            assertEquals(original.chunkIndex, embedded.chunkIndex)
            assertEquals(original.text, embedded.text)
            assertArrayEquals(embeddings[position], embedded.embedding)
        }
    }

    @Test
    fun `embeds a single chunk in one request`() {
        val event = event(1)
        val texts = event.chunks.map { it.text }
        val published = slot<Any>()
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(texts) } returns listOf(floatArrayOf(0.5f))
        every {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), capture(published))
        } returns completedSend()

        consumer.handle(event)

        coVerify(exactly = 1) { embeddingClient.embed(texts) }
        verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
        verify(exactly = 0) { statusUpdater.updateStatus(any(), MaterialStatus.FAILED, any(), any()) }
        val downstream = published.captured as ChunksEmbeddedEvent
        assertEquals(1, downstream.chunks.size)
    }

    @Test
    fun `embeds exactly 32 chunks without an empty extra request`() {
        val event = event(32)
        val texts = event.chunks.map { it.text }
        val published = slot<Any>()
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(texts) } returns List(32) { floatArrayOf(0.5f) }
        every {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), capture(published))
        } returns completedSend()

        consumer.handle(event)

        coVerify(exactly = 1) { embeddingClient.embed(any()) }
        verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
        verify(exactly = 0) { statusUpdater.updateStatus(any(), MaterialStatus.FAILED, any(), any()) }
        val downstream = published.captured as ChunksEmbeddedEvent
        assertEquals(32, downstream.chunks.size)
    }

    @Test
    fun `embeds 33 chunks in a full batch and a single chunk batch`() {
        val event = event(33)
        val firstBatch = event.chunks.take(32).map { it.text }
        val lastBatch = listOf(event.chunks.last().text)
        val published = slot<Any>()
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(firstBatch) } returns List(32) { floatArrayOf(0.5f) }
        coEvery { embeddingClient.embed(lastBatch) } returns listOf(floatArrayOf(0.75f))
        every {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), capture(published))
        } returns completedSend()

        consumer.handle(event)

        coVerify(exactly = 2) { embeddingClient.embed(any()) }
        verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
        verify(exactly = 0) { statusUpdater.updateStatus(any(), MaterialStatus.FAILED, any(), any()) }
        val downstream = published.captured as ChunksEmbeddedEvent
        assertEquals(33, downstream.chunks.size)
    }

    @Test
    fun `embeds exactly 64 chunks without an empty extra request`() {
        val event = event(64)
        val firstBatch = event.chunks.take(32).map { it.text }
        val secondBatch = event.chunks.drop(32).map { it.text }
        val published = slot<Any>()
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(firstBatch) } returns List(32) { floatArrayOf(0.5f) }
        coEvery { embeddingClient.embed(secondBatch) } returns List(32) { floatArrayOf(0.75f) }
        every {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), capture(published))
        } returns completedSend()

        consumer.handle(event)

        coVerify(exactly = 2) { embeddingClient.embed(any()) }
        verify(exactly = 1) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
        verify(exactly = 0) { statusUpdater.updateStatus(any(), MaterialStatus.FAILED, any(), any()) }
        val downstream = published.captured as ChunksEmbeddedEvent
        assertEquals(64, downstream.chunks.size)
    }

    @Test
    fun `second batch failure prevents publishing and stops remaining requests`() {
        val event = event(65)
        val firstBatch = event.chunks.take(32).map { it.text }
        val secondBatch = event.chunks.drop(32).take(32).map { it.text }
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(firstBatch) } returns List(32) { floatArrayOf(0.5f) }
        coEvery { embeddingClient.embed(secondBatch) } throws EmbeddingProviderException("Provider unavailable")

        consumer.handle(event)

        coVerify(exactly = 2) { embeddingClient.embed(any()) }
        verifyOrder {
            statusUpdater.updateStatus(event.materialId, MaterialStatus.FAILED, null, "Provider unavailable")
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.FAILED, "Provider unavailable"))
            processedEvents.markProcessed(event.eventId)
        }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
    }

    @Test
    fun `too few vectors in the second batch prevent publishing`() {
        val event = event(65)
        val firstBatch = event.chunks.take(32).map { it.text }
        val secondBatch = event.chunks.drop(32).take(32).map { it.text }
        val error = "Embedding provider returned 31 vectors for 32 chunks"
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(firstBatch) } returns List(32) { floatArrayOf(0.5f) }
        coEvery { embeddingClient.embed(secondBatch) } returns List(31) { floatArrayOf(0.5f) }

        consumer.handle(event)

        coVerify(exactly = 2) { embeddingClient.embed(any()) }
        verifyOrder {
            statusUpdater.updateStatus(event.materialId, MaterialStatus.FAILED, null, error)
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.FAILED, error))
            processedEvents.markProcessed(event.eventId)
        }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
    }

    @Test
    fun `too many vectors in the second batch prevent publishing`() {
        val event = event(65)
        val firstBatch = event.chunks.take(32).map { it.text }
        val secondBatch = event.chunks.drop(32).take(32).map { it.text }
        val error = "Embedding provider returned 33 vectors for 32 chunks"
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(firstBatch) } returns List(32) { floatArrayOf(0.5f) }
        coEvery { embeddingClient.embed(secondBatch) } returns List(33) { floatArrayOf(0.5f) }

        consumer.handle(event)

        coVerify(exactly = 2) { embeddingClient.embed(any()) }
        verifyOrder {
            statusUpdater.updateStatus(event.materialId, MaterialStatus.FAILED, null, error)
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.FAILED, error))
            processedEvents.markProcessed(event.eventId)
        }
        verify(exactly = 0) { kafkaTemplate.send(any<String>(), any<String>(), any<Any>()) }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
    }

    @Test
    fun `failed send marks the material failed instead of treating publication as successful`() {
        val event = event(1)
        val texts = event.chunks.map { it.text }
        val failedSend = CompletableFuture<SendResult<String, Any>>()
        failedSend.completeExceptionally(IllegalStateException("Kafka unavailable"))
        every { processedEvents.exists(event.eventId) } returns false
        coEvery { embeddingClient.embed(texts) } returns listOf(floatArrayOf(0.5f))
        every {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), any<Any>())
        } returns failedSend

        consumer.handle(event)

        verifyOrder {
            kafkaTemplate.send(chunksEmbeddedTopic, event.materialId.toString(), any<Any>())
            statusUpdater.updateStatus(event.materialId, MaterialStatus.FAILED, null, any())
            processedEvents.markProcessed(event.eventId)
        }
        verify(exactly = 1) { processedEvents.markProcessed(event.eventId) }
    }

    @Test
    fun `already processed event has no side effects`() {
        val event = event(65)
        every { processedEvents.exists(event.eventId) } returns true

        consumer.handle(event)

        verify(exactly = 1) { processedEvents.exists(event.eventId) }
        coVerify(exactly = 0) { embeddingClient.embed(any()) }
        verify(exactly = 0) {
            statusUpdater.updateStatus(any(), any(), any(), any())
            statusRelay.relay(any(), any())
            kafkaTemplate.send(any<String>(), any<String>(), any<Any>())
            processedEvents.markProcessed(any())
        }
    }

    private fun event(chunkCount: Int) = TextChunkedEvent(
        materialId = UUID.randomUUID(),
        courseId = UUID.randomUUID(),
        ownerId = UUID.randomUUID(),
        chunks = List(chunkCount) { position ->
            ChunkDto(chunkIndex = 1000 - position * 7, text = "Chunk text $position")
        },
    )

    private fun completedSend(): CompletableFuture<SendResult<String, Any>> =
        CompletableFuture.completedFuture(mockk())
}
