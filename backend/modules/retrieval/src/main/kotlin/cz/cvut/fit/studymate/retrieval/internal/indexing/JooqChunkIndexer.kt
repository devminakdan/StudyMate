package cz.cvut.fit.studymate.retrieval.internal.indexing

import com.pgvector.PGvector
import cz.cvut.fit.studymate.retrieval.api.ChunkIndexer
import cz.cvut.fit.studymate.retrieval.api.IndexedChunk
import org.jooq.DSLContext
import org.jooq.impl.DSL.field
import org.jooq.impl.DSL.name
import org.jooq.impl.DSL.table
import org.jooq.impl.SQLDataType
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

        dsl.deleteFrom(CHUNKS)
            .where(MATERIAL_ID.eq(materialId))
            .execute()

        if (chunks.isEmpty()) return

        val inserts = chunks.map { chunk ->
            dsl.insertInto(CHUNKS)
                .columns(ID, MATERIAL_ID, COURSE_ID, OWNER_ID, CHUNK_INDEX, CHUNK_TEXT, EMBEDDING)
                .values(
                    UUID.randomUUID(),
                    materialId,
                    courseId,
                    ownerId,
                    chunk.chunkIndex,
                    chunk.text,
                    PGvector(chunk.embedding),
                )
        }
        dsl.batch(inserts).execute()
    }

    private companion object {
        val CHUNKS = table(name("retrieval_chunks"))
        val ID = field(name("id"), UUID::class.java)
        val MATERIAL_ID = field(name("material_id"), UUID::class.java)
        val COURSE_ID = field(name("course_id"), UUID::class.java)
        val OWNER_ID = field(name("owner_id"), UUID::class.java)
        val CHUNK_INDEX = field(name("chunk_index"), Int::class.java)
        val CHUNK_TEXT = field(name("chunk_text"), String::class.java)
        // pgvector is a PostgreSQL extension type, not a type jOOQ knows how to
        // derive a DataType for. SQL OTHER lets the JDBC driver bind PGvector.
        val EMBEDDING = field(name("embedding"), SQLDataType.OTHER)
    }
}
