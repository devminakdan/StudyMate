package cz.cvut.fit.studymate.retrieval.internal.service

import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import cz.cvut.fit.studymate.course.api.CourseLookup
import cz.cvut.fit.studymate.retrieval.api.RetrievalLookup
import cz.cvut.fit.studymate.retrieval.api.SearchResultChunk
import cz.cvut.fit.studymate.retrieval.internal.exceptions.InvalidRetrievalRequestException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalAccessDeniedException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalCourseNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalMaterialNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.repository.ChunkSearchRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class RetrievalService(
    private val embeddingClient: EmbeddingClient,
    private val chunkSearchRepository: ChunkSearchRepository,
    private val courseLookup: CourseLookup,
) : RetrievalLookup {

    override suspend fun searchCourse(
        courseId: UUID,
        userId: UUID,
        query: String,
        limit: Int,
    ): List<SearchResultChunk> {
        val question = query.trim()
        if (question.isBlank()) {
            throw InvalidRetrievalRequestException("Search query must not be blank")
        }
        if (limit <= 0) {
            throw InvalidRetrievalRequestException("Search limit must be positive")
        }

        val course = courseLookup.findCourseById(courseId)
            ?: throw RetrievalCourseNotFoundException(courseId)
        if (course.ownerId != userId) {
            throw RetrievalAccessDeniedException(courseId)
        }

        val queryEmbedding = embeddingClient
            .embed(listOf(question))
            .single()

        return chunkSearchRepository.findNearest(
            courseId = courseId,
            userId = userId,
            queryEmbedding = queryEmbedding,
            limit = limit,
        )
    }

    override suspend fun searchMaterial(
        materialId: UUID,
        courseId: UUID,
        userId: UUID,
        query: String,
        limit: Int,
    ): List<SearchResultChunk> {
        val question = query.trim()
        if (question.isBlank()) {
            throw InvalidRetrievalRequestException("Search query must not be blank")
        }
        if (limit <= 0) {
            throw InvalidRetrievalRequestException("Search limit must be positive")
        }

        val course = courseLookup.findCourseById(courseId)
            ?: throw RetrievalCourseNotFoundException(courseId)
        if (course.ownerId != userId) {
            throw RetrievalAccessDeniedException(courseId)
        }

        val material = courseLookup.findMaterialById(materialId)
            ?: throw RetrievalMaterialNotFoundException(materialId)
        if (material.courseId != courseId) {
            throw RetrievalMaterialNotFoundException(materialId)
        }

        val queryEmbedding = embeddingClient
            .embed(listOf(question))
            .single()

        return chunkSearchRepository.findNearestInMaterial(
            materialId = materialId,
            courseId = courseId,
            userId = userId,
            queryEmbedding = queryEmbedding,
            limit = limit,
        )
    }
}
