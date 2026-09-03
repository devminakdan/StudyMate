package cz.cvut.fit.studymate.ingestion.internal.indexing

import cz.cvut.fit.studymate.ingestion.internal.kafka.event.ChunkWithEmbeddingDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Integration seam for the retrieval module.  The retrieval module currently exposes no
 * public API, so this contract intentionally lives in ingestion until retrieval provides
 * its real vector-store implementation.
 */
internal interface ChunkIndexer {
    fun indexChunks(
        materialId: UUID,
        courseId: UUID,
        ownerId: UUID,
        chunks: List<ChunkWithEmbeddingDto>,
    )
}

/**
 * Temporary application-startup implementation while :retrieval is not implemented.
 * It fails deliberately: marking a material READY without persisting its vectors would
 * present an unusable material to the user.
 */
@Component
internal class NoOpChunkIndexer : ChunkIndexer {
    override fun indexChunks(
        materialId: UUID,
        courseId: UUID,
        ownerId: UUID,
        chunks: List<ChunkWithEmbeddingDto>,
    ) {
        val message = "Retrieval is unavailable; cannot index ${chunks.size} chunks for material $materialId"
        logger?.warn(message)
        throw UnsupportedOperationException(message)
    }

    private companion object {
        val logger: Logger? = LoggerFactory.getLogger(NoOpChunkIndexer::class.java)
    }
}
