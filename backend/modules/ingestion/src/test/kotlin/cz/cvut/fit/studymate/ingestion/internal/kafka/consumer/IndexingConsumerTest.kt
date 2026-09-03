package cz.cvut.fit.studymate.ingestion.internal.kafka.consumer

import com.fasterxml.jackson.databind.ObjectMapper
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.course.api.MaterialStatusUpdater
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunkWithEmbeddingDto
import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunksEmbeddedEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEvent
import cz.cvut.fit.studymate.ingestion.internal.notification.StatusEventRelay
import cz.cvut.fit.studymate.ingestion.internal.persistence.ProcessedEventsRepository
import cz.cvut.fit.studymate.retrieval.api.ChunkIndexer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class IndexingConsumerTest {
    private val objectMapper = mockk<ObjectMapper>()
    private val processedEvents = mockk<ProcessedEventsRepository>()
    private val statusUpdater = mockk<MaterialStatusUpdater>(relaxed = true)
    private val statusRelay = mockk<StatusEventRelay>(relaxed = true)
    private val chunkIndexer = mockk<ChunkIndexer>(relaxed = true)
    private lateinit var consumer: IndexingConsumer

    @BeforeEach
    fun setUp() {
        consumer = IndexingConsumer(
            objectMapper = objectMapper,
            processedEventsRepository = processedEvents,
            materialStatusUpdater = statusUpdater,
            statusEventRelay = statusRelay,
            chunkIndexer = chunkIndexer,
        )
    }

    @Test
    fun `indexes embedded chunks with the retrieval API and marks material ready`() {
        val event = event()
        every { processedEvents.exists(event.eventId) } returns false
        val indexedChunks = slot<List<cz.cvut.fit.studymate.retrieval.api.IndexedChunk>>()

        consumer.handle(event)

        verifyOrder {
            statusUpdater.updateStatus(event.materialId, MaterialStatus.INDEXING, null, null)
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.INDEXING))
            chunkIndexer.indexChunks(
                materialId = event.materialId,
                courseId = event.courseId,
                ownerId = event.ownerId,
                chunks = capture(indexedChunks),
            )
            statusUpdater.updateStatus(event.materialId, MaterialStatus.READY, null, null)
            statusRelay.relay(event.ownerId, StatusEvent(event.materialId, MaterialStatus.READY))
            processedEvents.markProcessed(event.eventId)
        }
        val indexedChunk = indexedChunks.captured.single()
        check(indexedChunk.chunkIndex == 4)
        check(indexedChunk.text == "Index this text")
        check(indexedChunk.embedding.contentEquals(floatArrayOf(0.25f, 0.75f)))
    }

    @Test
    fun `does nothing when event was already processed`() {
        val event = event()
        every { processedEvents.exists(event.eventId) } returns true

        consumer.handle(event)

        verify(exactly = 1) { processedEvents.exists(event.eventId) }
        verify(exactly = 0) {
            statusUpdater.updateStatus(any(), any(), any(), any())
            statusRelay.relay(any(), any())
            chunkIndexer.indexChunks(any(), any(), any(), any())
            processedEvents.markProcessed(any())
        }
    }

    private fun event() = ChunksEmbeddedEvent(
        eventId = UUID.randomUUID(),
        materialId = UUID.randomUUID(),
        courseId = UUID.randomUUID(),
        ownerId = UUID.randomUUID(),
        chunks = listOf(ChunkWithEmbeddingDto(4, "Index this text", floatArrayOf(0.25f, 0.75f))),
    )
}
