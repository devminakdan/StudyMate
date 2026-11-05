package cz.cvut.fit.studymate.retrieval.internal.service

import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import cz.cvut.fit.studymate.course.api.Course
import cz.cvut.fit.studymate.course.api.CourseLookup
import cz.cvut.fit.studymate.course.api.Material
import cz.cvut.fit.studymate.course.api.MaterialStatus
import cz.cvut.fit.studymate.retrieval.api.SearchResultChunk
import cz.cvut.fit.studymate.retrieval.internal.exceptions.InvalidRetrievalRequestException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalAccessDeniedException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalCourseNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalMaterialNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.repository.ChunkSearchRepository
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import java.util.UUID

internal class RetrievalServiceTest {

    private val embeddingClient = mockk<EmbeddingClient>()
    private val chunkSearchRepository = mockk<ChunkSearchRepository>()
    private val courseLookup = mockk<CourseLookup>()
    private val service = RetrievalService(embeddingClient, chunkSearchRepository, courseLookup)

    private fun course(
        id: UUID = UUID.randomUUID(),
        ownerId: UUID = UUID.randomUUID(),
        name: String = "Database Systems",
        code: String? = "BIE-DBS",
        description: String? = null,
        createdAt: OffsetDateTime = OffsetDateTime.now(),
        updatedAt: OffsetDateTime = OffsetDateTime.now(),
    ) = Course(id, ownerId, name, code, description, createdAt, updatedAt)

    private fun material(
        id: UUID = UUID.randomUUID(),
        courseId: UUID = UUID.randomUUID(),
        originalFilename: String = "lecture.pdf",
        storagePath: String = "storage/path",
        mimeType: String = "application/pdf",
        sizeBytes: Long = 1024L,
        status: MaterialStatus = MaterialStatus.READY,
        pageCount: Int? = 10,
        uploadedAt: OffsetDateTime = OffsetDateTime.now(),
        processedAt: OffsetDateTime? = OffsetDateTime.now(),
        errorMessage: String? = null,
    ) = Material(id, courseId, originalFilename, storagePath, mimeType, sizeBytes, status, pageCount, uploadedAt, processedAt, errorMessage)

    private fun searchResultChunk(
        chunkId: UUID = UUID.randomUUID(),
        materialId: UUID = UUID.randomUUID(),
        chunkIndex: Int = 0,
        text: String = "Chunk text",
        distance: Double = 0.05,
    ) = SearchResultChunk(chunkId, materialId, chunkIndex, text, distance)

    @Test
    fun `searchCourse throws InvalidRetrievalRequestException when query is blank`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val exception = assertThrows<InvalidRetrievalRequestException> {
            service.searchCourse(courseId, userId, "   ", limit = 5)
        }

        assertThat(exception.message).contains("Search query must not be blank")
        verify { courseLookup wasNot Called }
        verify { embeddingClient wasNot Called }
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchCourse throws InvalidRetrievalRequestException when limit is zero or negative`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        val exZero = assertThrows<InvalidRetrievalRequestException> {
            service.searchCourse(courseId, userId, "What is a transaction?", limit = 0)
        }
        assertThat(exZero.message).contains("Search limit must be positive")

        val exNegative = assertThrows<InvalidRetrievalRequestException> {
            service.searchCourse(courseId, userId, "What is a transaction?", limit = -1)
        }
        assertThat(exNegative.message).contains("Search limit must be positive")

        verify { courseLookup wasNot Called }
        verify { embeddingClient wasNot Called }
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchCourse throws RetrievalCourseNotFoundException when course does not exist`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()

        every { courseLookup.findCourseById(courseId) } returns null

        val exception = assertThrows<RetrievalCourseNotFoundException> {
            service.searchCourse(courseId, userId, "What is atomicity?", limit = 5)
        }

        assertThat(exception.message).contains("Course not found: $courseId")
        verify(exactly = 1) { courseLookup.findCourseById(courseId) }
        verify { embeddingClient wasNot Called }
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchCourse throws RetrievalAccessDeniedException when caller does not own the course`() = runBlocking {
        val courseId = UUID.randomUUID()
        val realOwnerId = UUID.randomUUID()
        val callerId = UUID.randomUUID()
        val course = course(id = courseId, ownerId = realOwnerId)

        every { courseLookup.findCourseById(courseId) } returns course

        val exception = assertThrows<RetrievalAccessDeniedException> {
            service.searchCourse(courseId, callerId, "What is atomicity?", limit = 5)
        }

        assertThat(exception.message).contains("Access to course $courseId is denied")
        verify(exactly = 1) { courseLookup.findCourseById(courseId) }
        verify { embeddingClient wasNot Called }
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchCourse trims query, generates embedding, and returns nearest chunks when valid`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val course = course(id = courseId, ownerId = userId)
        val queryEmbedding = floatArrayOf(0.12f, 0.45f, -0.67f)
        val expectedChunks = listOf(
            searchResultChunk(chunkIndex = 0, text = "First relevant chunk", distance = 0.04),
            searchResultChunk(chunkIndex = 1, text = "Second relevant chunk", distance = 0.15),
        )

        every { courseLookup.findCourseById(courseId) } returns course
        coEvery { embeddingClient.embed(listOf("What is ACID?")) } returns listOf(queryEmbedding)
        every {
            chunkSearchRepository.findNearest(courseId, userId, queryEmbedding, limit = 5)
        } returns expectedChunks

        val result = service.searchCourse(
            courseId = courseId,
            userId = userId,
            query = "   What is ACID?   ",
            limit = 5,
        )

        assertThat(result).isEqualTo(expectedChunks)
        verify(exactly = 1) { courseLookup.findCourseById(courseId) }
        coVerify(exactly = 1) { embeddingClient.embed(listOf("What is ACID?")) }
        verify(exactly = 1) {
            chunkSearchRepository.findNearest(courseId, userId, queryEmbedding, limit = 5)
        }
    }

    @Test
    fun `searchCourse propagates exception when embedding provider fails`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val course = course(id = courseId, ownerId = userId)

        every { courseLookup.findCourseById(courseId) } returns course
        coEvery { embeddingClient.embed(any()) } throws EmbeddingProviderException("Ollama service unavailable")

        val exception = assertThrows<EmbeddingProviderException> {
            service.searchCourse(courseId, userId, "What is ACID?", limit = 5)
        }

        assertThat(exception.message).contains("Ollama service unavailable")
        verify(exactly = 1) { courseLookup.findCourseById(courseId) }
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchMaterial throws RetrievalMaterialNotFoundException when material does not exist`() = runBlocking {
        val courseId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val course = course(id = courseId, ownerId = userId)

        every { courseLookup.findCourseById(courseId) } returns course
        every { courseLookup.findMaterialById(materialId) } returns null

        val exception = assertThrows<RetrievalMaterialNotFoundException> {
            service.searchMaterial(materialId, courseId, userId, "What is on slide 2?", limit = 5)
        }

        assertThat(exception.message).contains("Material $materialId was not found")
        verify(exactly = 1) { courseLookup.findMaterialById(materialId) }
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchMaterial throws RetrievalMaterialNotFoundException when material belongs to different course`() = runBlocking {
        val courseId = UUID.randomUUID()
        val anotherCourseId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val course = course(id = courseId, ownerId = userId)
        val foreignMaterial = material(id = materialId, courseId = anotherCourseId)

        every { courseLookup.findCourseById(courseId) } returns course
        every { courseLookup.findMaterialById(materialId) } returns foreignMaterial

        val exception = assertThrows<RetrievalMaterialNotFoundException> {
            service.searchMaterial(materialId, courseId, userId, "What is on slide 2?", limit = 5)
        }

        assertThat(exception.message).contains("Material $materialId was not found")
        verify { chunkSearchRepository wasNot Called }
    }

    @Test
    fun `searchMaterial trims query, generates embedding, and returns nearest chunks for material`() = runBlocking {
        val courseId = UUID.randomUUID()
        val materialId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val course = course(id = courseId, ownerId = userId)
        val material = material(id = materialId, courseId = courseId)
        val queryEmbedding = floatArrayOf(0.33f, 0.44f)
        val expectedChunks = listOf(
            searchResultChunk(materialId = materialId, chunkIndex = 2, text = "Material specific chunk", distance = 0.08),
        )

        every { courseLookup.findCourseById(courseId) } returns course
        every { courseLookup.findMaterialById(materialId) } returns material
        coEvery { embeddingClient.embed(listOf("What is B-Tree?")) } returns listOf(queryEmbedding)
        every {
            chunkSearchRepository.findNearestInMaterial(materialId, courseId, userId, queryEmbedding, limit = 5)
        } returns expectedChunks

        val result = service.searchMaterial(
            materialId = materialId,
            courseId = courseId,
            userId = userId,
            query = "   What is B-Tree?   ",
            limit = 5,
        )

        assertThat(result).isEqualTo(expectedChunks)
        verify(exactly = 1) { courseLookup.findCourseById(courseId) }
        verify(exactly = 1) { courseLookup.findMaterialById(materialId) }
        coVerify(exactly = 1) { embeddingClient.embed(listOf("What is B-Tree?")) }
        verify(exactly = 1) {
            chunkSearchRepository.findNearestInMaterial(materialId, courseId, userId, queryEmbedding, limit = 5)
        }
    }
}
