package cz.cvut.fit.studymate.retrieval.api

import cz.cvut.fit.studymate.retrieval.internal.exceptions.InvalidRetrievalRequestException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalAccessDeniedException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalCourseNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalMaterialNotFoundException
import java.util.UUID

interface RetrievalLookup {
    /**
     * Finds source fragments in a course owned by [userId].
     *
     * @throws InvalidRetrievalRequestException if the query is blank or the limit is not positive.
     * @throws RetrievalCourseNotFoundException if the course does not exist.
     * @throws RetrievalAccessDeniedException if the user does not own the course.
     */
    suspend fun searchCourse(
        courseId: UUID,
        userId: UUID,
        query: String,
        limit: Int = 5,
    ): List<SearchResultChunk>

    /**
     * Finds source fragments inside a specific material [materialId] belonging to [courseId] and owned by [userId].
     *
     * @throws InvalidRetrievalRequestException if the query is blank or the limit is not positive.
     * @throws RetrievalCourseNotFoundException if the course does not exist.
     * @throws RetrievalAccessDeniedException if the user does not own the course.
     * @throws RetrievalMaterialNotFoundException if the material does not exist or does not belong to the course.
     */
    suspend fun searchMaterial(
        materialId: UUID,
        courseId: UUID,
        userId: UUID,
        query: String,
        limit: Int = 5,
    ): List<SearchResultChunk>
}
