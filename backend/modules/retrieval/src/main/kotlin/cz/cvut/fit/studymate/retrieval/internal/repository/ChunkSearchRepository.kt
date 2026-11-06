package cz.cvut.fit.studymate.retrieval.internal.repository

import com.pgvector.PGvector
import cz.cvut.fit.studymate.retrieval.api.SearchResultChunk
import cz.cvut.fit.studymate.retrieval.generated.tables.references.RETRIEVAL_CHUNKS
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DSL.field
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
internal class ChunkSearchRepository(
    private val dsl: DSLContext,
) {
    fun findNearest(
        courseId: UUID,
        userId: UUID,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<SearchResultChunk> {
        require(queryEmbedding.isNotEmpty()) { "Query embedding must not be empty" }
        require(limit > 0) { "Search limit must be positive" }

        val distance = field(
            "{0} <=> cast({1} as vector)",
            Double::class.java,
            RETRIEVAL_CHUNKS.EMBEDDING,
            DSL.`val`(PGvector(queryEmbedding).value),
        ).`as`("distance")

        return dsl.select(
            RETRIEVAL_CHUNKS.ID.`as`("chunkId"),
            RETRIEVAL_CHUNKS.MATERIAL_ID.`as`("materialId"),
            RETRIEVAL_CHUNKS.CHUNK_INDEX.`as`("chunkIndex"),
            RETRIEVAL_CHUNKS.CHUNK_TEXT.`as`("text"),
            distance,
        )
            .from(RETRIEVAL_CHUNKS)
            .where(RETRIEVAL_CHUNKS.COURSE_ID.eq(courseId))
            .and(RETRIEVAL_CHUNKS.OWNER_ID.eq(userId))
            .orderBy(distance.asc())
            .limit(limit)
            .fetchInto(SearchResultChunk::class.java)
    }

    fun findNearestInMaterial(
        materialId: UUID,
        courseId: UUID,
        userId: UUID,
        queryEmbedding: FloatArray,
        limit: Int,
    ): List<SearchResultChunk> {
        require(queryEmbedding.isNotEmpty()) { "Query embedding must not be empty" }
        require(limit > 0) { "Search limit must be positive" }

        val distance = field(
            "{0} <=> cast({1} as vector)",
            Double::class.java,
            RETRIEVAL_CHUNKS.EMBEDDING,
            DSL.`val`(PGvector(queryEmbedding).value),
        ).`as`("distance")

        return dsl.select(
            RETRIEVAL_CHUNKS.ID.`as`("chunkId"),
            RETRIEVAL_CHUNKS.MATERIAL_ID.`as`("materialId"),
            RETRIEVAL_CHUNKS.CHUNK_INDEX.`as`("chunkIndex"),
            RETRIEVAL_CHUNKS.CHUNK_TEXT.`as`("text"),
            distance,
        )
            .from(RETRIEVAL_CHUNKS)
            .where(RETRIEVAL_CHUNKS.MATERIAL_ID.eq(materialId))
            .and(RETRIEVAL_CHUNKS.COURSE_ID.eq(courseId))
            .and(RETRIEVAL_CHUNKS.OWNER_ID.eq(userId))
            .orderBy(distance.asc())
            .limit(limit)
            .fetchInto(SearchResultChunk::class.java)
    }
}
