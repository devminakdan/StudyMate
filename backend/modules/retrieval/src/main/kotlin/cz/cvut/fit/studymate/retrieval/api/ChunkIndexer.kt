package cz.cvut.fit.studymate.retrieval.api

import java.util.UUID

/**
 * Persists a material's embedded chunks in the retrieval store.
 *
 * Implementations replace the complete set of chunks for [materialId], which makes a
 * retry after a partially completed indexing attempt safe.
 */
interface ChunkIndexer {
    fun indexChunks(
        materialId: UUID,
        courseId: UUID,
        ownerId: UUID,
        chunks: List<IndexedChunk>,
    )
}

/** A text fragment and the embedding produced for it by the embedding provider. */
data class IndexedChunk(
    val chunkIndex: Int,
    val text: String,
    val embedding: FloatArray,
)
