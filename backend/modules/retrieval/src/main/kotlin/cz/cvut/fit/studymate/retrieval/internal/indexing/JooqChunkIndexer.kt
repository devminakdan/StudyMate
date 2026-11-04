package cz.cvut.fit.studymate.retrieval.internal.indexing

import com.pgvector.PGvector
import cz.cvut.fit.studymate.retrieval.api.ChunkIndexer
import cz.cvut.fit.studymate.retrieval.api.IndexedChunk
import cz.cvut.fit.studymate.retrieval.generated.tables.references.RETRIEVAL_CHUNKS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * PostgreSQL/pgvector implementation of the ingestion boundary.
 *
 * Replacing the material's existing rows in one transaction means that a Kafka retry
 * cannot leave a material with a mixture of old and newly generated embeddings.
 */
@Repository
internal class JooqChunkIndexer(
    private val dsl: DSLContext,
) : ChunkIndexer {

    @Transactional
    override fun indexChunks(
        materialId: UUID,
        courseId: UUID,
        ownerId: UUID,
        chunks: List<IndexedChunk>,
    ) {
        require(chunks.map(IndexedChunk::chunkIndex).distinct().size == chunks.size) {
            "Chunk indices must be unique for material $materialId"
        }
        require(chunks.all { it.embedding.isNotEmpty() }) {
            "Chunk embeddings must not be empty for material $materialId"
        }

        dsl.deleteFrom(RETRIEVAL_CHUNKS)
            .where(RETRIEVAL_CHUNKS.MATERIAL_ID.eq(materialId))
            .execute()

        if (chunks.isEmpty()) return

        val inserts = chunks.map { chunk ->
            dsl.insertInto(RETRIEVAL_CHUNKS)
                .columns(
                    RETRIEVAL_CHUNKS.ID,
                    RETRIEVAL_CHUNKS.MATERIAL_ID,
                    RETRIEVAL_CHUNKS.COURSE_ID,
                    RETRIEVAL_CHUNKS.OWNER_ID,
                    RETRIEVAL_CHUNKS.CHUNK_INDEX,
                    RETRIEVAL_CHUNKS.CHUNK_TEXT,
                    RETRIEVAL_CHUNKS.EMBEDDING,
                )
                .values(
                    UUID.randomUUID(),
                    materialId,
                    courseId,
                    ownerId,
                    chunk.chunkIndex,
                    chunk.text,
                    DSL.field("cast(? as vector)", PGvector(chunk.embedding).value),
                )
        }
        dsl.batch(inserts).execute()
    }
}
