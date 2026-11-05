package cz.cvut.fit.studymate.retrieval.internal.repository

import com.pgvector.PGvector
import cz.cvut.fit.studymate.course.generated.tables.references.COURSES
import cz.cvut.fit.studymate.course.generated.tables.references.MATERIALS
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.iam.generated.tables.references.USERS
import cz.cvut.fit.studymate.retrieval.generated.tables.references.RETRIEVAL_CHUNKS
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.UUID

@SpringBootTest(
    classes = [ChunkSearchRepositoryTest.TestConfig::class],
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@Transactional
internal class ChunkSearchRepositoryTest {

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(ChunkSearchRepository::class)
    class TestConfig {
        @Bean
        @ServiceConnection
        fun postgres(): PostgreSQLContainer<*> =
            PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"))
    }

    @Autowired
    private lateinit var repository: ChunkSearchRepository

    @Autowired
    private lateinit var dsl: DSLContext

    private fun createTestUser(): UUID =
        dsl.insertInto(USERS)
            .set(USERS.EMAIL, "user-${UUID.randomUUID()}@example.com")
            .set(USERS.PASSWORD_HASH, "hash")
            .set(USERS.USERNAME, "user")
            .set(USERS.ROLE, Role.USER.name)
            .returningResult(USERS.ID)
            .fetchOne()!!
            .value1()!!

    private fun createTestCourse(ownerId: UUID): UUID =
        dsl.insertInto(COURSES)
            .set(COURSES.OWNER_ID, ownerId)
            .set(COURSES.NAME, "Course-${UUID.randomUUID()}")
            .returningResult(COURSES.ID)
            .fetchOne()!!
            .value1()!!

    private fun createTestMaterial(courseId: UUID): UUID =
        dsl.insertInto(MATERIALS)
            .set(MATERIALS.COURSE_ID, courseId)
            .set(MATERIALS.ORIGINAL_FILENAME, "test.pdf")
            .set(MATERIALS.STORAGE_PATH, "storage/path/${UUID.randomUUID()}")
            .set(MATERIALS.MIME_TYPE, "application/pdf")
            .set(MATERIALS.SIZE_BYTES, 1024L)
            .returningResult(MATERIALS.ID)
            .fetchOne()!!
            .value1()!!

    private fun insertChunk(
        materialId: UUID,
        courseId: UUID,
        ownerId: UUID,
        chunkIndex: Int,
        text: String,
        embedding: FloatArray,
    ): UUID {
        val chunkId = UUID.randomUUID()
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
                chunkId,
                materialId,
                courseId,
                ownerId,
                chunkIndex,
                text,
                DSL.field("cast(? as vector)", PGvector(embedding).value),
            )
            .execute()
        return chunkId
    }

    private fun unitVector(firstComponent: Float): FloatArray =
        FloatArray(1024).apply { this[0] = firstComponent }

    @Test
    fun `findNearest returns chunks ordered by ascending cosine distance`() {
        val userId = createTestUser()
        val courseId = createTestCourse(userId)
        val materialId = createTestMaterial(courseId)

        // Query vector points in positive direction of axis 0: [1.0, 0, 0, ...]
        val query = unitVector(1.0f)

        // Chunk 1: identical direction [1.0, 0, 0, ...] -> cosine distance = 0.0
        val idExact = insertChunk(materialId, courseId, userId, 0, "Exact match", unitVector(1.0f))

        // Chunk 2: orthogonal [0, 1.0, 0, ...] -> cosine distance = 1.0
        val orthogonal = FloatArray(1024).apply { this[1] = 1.0f }
        val idOrthogonal = insertChunk(materialId, courseId, userId, 1, "Orthogonal", orthogonal)

        // Chunk 3: opposite direction [-1.0, 0, 0, ...] -> cosine distance = 2.0
        val idOpposite = insertChunk(materialId, courseId, userId, 2, "Opposite", unitVector(-1.0f))

        val results = repository.findNearest(courseId, userId, query, limit = 10)

        assertThat(results).hasSize(3)
        assertThat(results[0].chunkId).isEqualTo(idExact)
        assertThat(results[0].distance).isCloseTo(0.0, org.assertj.core.data.Offset.offset(0.001))

        assertThat(results[1].chunkId).isEqualTo(idOrthogonal)
        assertThat(results[1].distance).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.001))

        assertThat(results[2].chunkId).isEqualTo(idOpposite)
        assertThat(results[2].distance).isCloseTo(2.0, org.assertj.core.data.Offset.offset(0.001))
    }

    @Test
    fun `findNearest filters strictly by courseId`() {
        val userId = createTestUser()
        val courseA = createTestCourse(userId)
        val courseB = createTestCourse(userId)
        val materialA = createTestMaterial(courseA)
        val materialB = createTestMaterial(courseB)

        insertChunk(materialA, courseA, userId, 0, "Course A chunk", unitVector(1.0f))
        insertChunk(materialB, courseB, userId, 0, "Course B chunk", unitVector(1.0f))

        val resultsA = repository.findNearest(courseA, userId, unitVector(1.0f), limit = 10)

        assertThat(resultsA).hasSize(1)
        assertThat(resultsA.single().text).isEqualTo("Course A chunk")
    }

    @Test
    fun `findNearest filters strictly by userId`() {
        val userA = createTestUser()
        val userB = createTestUser()
        val courseA = createTestCourse(userA)
        val materialA = createTestMaterial(courseA)

        insertChunk(materialA, courseA, userA, 0, "User A chunk", unitVector(1.0f))

        // Querying with userB should return nothing even with matching courseId
        val results = repository.findNearest(courseA, userB, unitVector(1.0f), limit = 10)

        assertThat(results).isEmpty()
    }

    @Test
    fun `findNearest respects the limit parameter`() {
        val userId = createTestUser()
        val courseId = createTestCourse(userId)
        val materialId = createTestMaterial(courseId)

        repeat(5) { i ->
            insertChunk(materialId, courseId, userId, i, "Chunk $i", unitVector(1.0f))
        }

        val results = repository.findNearest(courseId, userId, unitVector(1.0f), limit = 2)

        assertThat(results).hasSize(2)
    }

    @Test
    fun `findNearestInMaterial filters strictly by materialId`() {
        val userId = createTestUser()
        val courseId = createTestCourse(userId)
        val materialA = createTestMaterial(courseId)
        val materialB = createTestMaterial(courseId)

        insertChunk(materialA, courseId, userId, 0, "Material A chunk", unitVector(1.0f))
        insertChunk(materialB, courseId, userId, 0, "Material B chunk", unitVector(1.0f))

        val results = repository.findNearestInMaterial(materialA, courseId, userId, unitVector(1.0f), limit = 10)

        assertThat(results).hasSize(1)
        assertThat(results.single().text).isEqualTo("Material A chunk")
        assertThat(results.single().materialId).isEqualTo(materialA)
    }

    @Test
    fun `findNearest rejects empty query embedding or nonpositive limit`() {
        val dummyId = UUID.randomUUID()

        assertThrows<IllegalArgumentException> {
            repository.findNearest(dummyId, dummyId, floatArrayOf(), limit = 5)
        }

        assertThrows<IllegalArgumentException> {
            repository.findNearest(dummyId, dummyId, unitVector(1.0f), limit = 0)
        }
    }
}
