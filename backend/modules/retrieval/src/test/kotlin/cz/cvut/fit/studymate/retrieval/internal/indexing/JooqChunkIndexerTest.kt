package cz.cvut.fit.studymate.retrieval.internal.indexing

import cz.cvut.fit.studymate.retrieval.api.IndexedChunk
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class JooqChunkIndexerTest {
    private val dsl = mockk<DSLContext>(relaxed = true)
    private val indexer = JooqChunkIndexer(dsl)

    @Test
    fun `rejects duplicate chunk indexes before removing the existing material index`() {
        val materialId = UUID.randomUUID()

        val exception = assertThrows<IllegalArgumentException> {
            indexer.indexChunks(
                materialId = materialId,
                courseId = UUID.randomUUID(),
                ownerId = UUID.randomUUID(),
                chunks = listOf(chunk(index = 0), chunk(index = 0)),
            )
        }

        assertThat(exception).hasMessageContaining("Chunk indices must be unique")
        verify { dsl wasNot Called }
    }

    @Test
    fun `rejects empty embeddings before removing the existing material index`() {
        val materialId = UUID.randomUUID()

        val exception = assertThrows<IllegalArgumentException> {
            indexer.indexChunks(
                materialId = materialId,
                courseId = UUID.randomUUID(),
                ownerId = UUID.randomUUID(),
                chunks = listOf(chunk(embedding = floatArrayOf())),
            )
        }

        assertThat(exception).hasMessageContaining("Chunk embeddings must not be empty")
        verify { dsl wasNot Called }
    }

    private fun chunk(
        index: Int = 0,
        embedding: FloatArray = floatArrayOf(0.1f, 0.2f),
    ) = IndexedChunk(index, "A chunk of source text", embedding)
}
