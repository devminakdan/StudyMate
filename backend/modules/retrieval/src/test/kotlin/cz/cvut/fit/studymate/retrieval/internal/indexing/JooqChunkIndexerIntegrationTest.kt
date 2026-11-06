package cz.cvut.fit.studymate.retrieval.internal.indexing

import cz.cvut.fit.studymate.course.generated.tables.references.COURSES
import cz.cvut.fit.studymate.course.generated.tables.references.MATERIALS
import cz.cvut.fit.studymate.iam.generated.tables.references.USERS
import cz.cvut.fit.studymate.retrieval.api.IndexedChunk
import cz.cvut.fit.studymate.retrieval.internal.repository.ChunkSearchRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.dao.DataAccessException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@SpringBootTest(
    classes = [JooqChunkIndexerIntegrationTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
internal class JooqChunkIndexerIntegrationTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(JooqChunkIndexer::class, ChunkSearchRepository::class)
    class TestConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
    }

    @Autowired private lateinit var indexer: JooqChunkIndexer
    @Autowired private lateinit var search: ChunkSearchRepository
    @Autowired private lateinit var dsl: DSLContext

    private val userId = UUID.randomUUID()
    private val courseId = UUID.randomUUID()
    private val materialId = UUID.randomUUID()
    private val otherMaterialId = UUID.randomUUID()
    private val query = FloatArray(1024).apply { this[0] = 1f }

    @BeforeEach
    fun createMaterialOwners() {
        dsl.insertInto(USERS)
            .set(USERS.ID, userId)
            .set(USERS.EMAIL, "$userId@example.com")
            .set(USERS.USERNAME, "indexer-test")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.ROLE, "USER")
            .execute()
        dsl.insertInto(COURSES)
            .set(COURSES.ID, courseId)
            .set(COURSES.OWNER_ID, userId)
            .set(COURSES.NAME, "Indexer test")
            .execute()
        for (id in listOf(materialId, otherMaterialId)) {
            dsl.insertInto(MATERIALS)
                .set(MATERIALS.ID, id)
                .set(MATERIALS.COURSE_ID, courseId)
                .set(MATERIALS.ORIGINAL_FILENAME, "lecture.pdf")
                .set(MATERIALS.STORAGE_PATH, "test/$id")
                .set(MATERIALS.MIME_TYPE, "application/pdf")
                .set(MATERIALS.SIZE_BYTES, 1024L)
                .execute()
        }
    }

    @AfterEach
    fun removeMaterialOwners() {
        dsl.deleteFrom(COURSES).where(COURSES.ID.eq(courseId)).execute()
        dsl.deleteFrom(USERS).where(USERS.ID.eq(userId)).execute()
    }

    @Test
    fun `indexed chunks can be searched with their text indexes and cosine distances`() {
        val orthogonal = FloatArray(1024).apply { this[1] = 1f }
        indexer.indexChunks(materialId, courseId, userId, listOf(
            IndexedChunk(0, "Exact match", query),
            IndexedChunk(1, "Orthogonal match", orthogonal),
        ))

        val results = search.findNearest(courseId, userId, query, limit = 5)

        assertThat(results.map { it.text }).containsExactly("Exact match", "Orthogonal match")
        assertThat(results.map { it.chunkIndex }).containsExactly(0, 1)
        assertThat(results.map { it.materialId }).containsOnly(materialId)
        assertThat(results[0].distance).isCloseTo(0.0, offset(0.001))
        assertThat(results[1].distance).isCloseTo(1.0, offset(0.001))
    }

    @Test
    fun `reindexing replaces the entire material index without touching other materials`() {
        indexer.indexChunks(materialId, courseId, userId, listOf(
            IndexedChunk(0, "Old first", query),
            IndexedChunk(1, "Old second", query),
        ))
        indexer.indexChunks(otherMaterialId, courseId, userId, listOf(IndexedChunk(0, "Other material", query)))

        repeat(2) {
            indexer.indexChunks(materialId, courseId, userId, listOf(IndexedChunk(0, "Replacement", query)))
        }

        assertThat(search.findNearestInMaterial(materialId, courseId, userId, query, 5).map { it.text })
            .containsExactly("Replacement")
        assertThat(search.findNearestInMaterial(otherMaterialId, courseId, userId, query, 5).map { it.text })
            .containsExactly("Other material")
        assertThat(search.findNearest(courseId, userId, query, 5)).hasSize(2)
    }

    @Test
    fun `failed replacement rolls back deletion and partial inserts preserving the old chunks`() {
        indexer.indexChunks(materialId, courseId, userId, listOf(IndexedChunk(0, "Original", query)))
        val original = search.findNearestInMaterial(materialId, courseId, userId, query, 5)

        assertThrows<DataAccessException> {
            indexer.indexChunks(materialId, courseId, userId, listOf(
                IndexedChunk(0, "Would replace original", query),
                IndexedChunk(1, "Wrong dimensions", floatArrayOf(1f)),
            ))
        }

        assertThat(search.findNearestInMaterial(materialId, courseId, userId, query, 5)).isEqualTo(original)
    }

    @Test
    fun `empty index removes only the target material chunks`() {
        indexer.indexChunks(materialId, courseId, userId, listOf(IndexedChunk(0, "Target", query)))
        indexer.indexChunks(otherMaterialId, courseId, userId, listOf(IndexedChunk(0, "Other material", query)))

        indexer.indexChunks(materialId, courseId, userId, emptyList())

        assertThat(search.findNearestInMaterial(materialId, courseId, userId, query, 5)).isEmpty()
        assertThat(search.findNearest(courseId, userId, query, 5).map { it.text }).containsExactly("Other material")
    }
}
