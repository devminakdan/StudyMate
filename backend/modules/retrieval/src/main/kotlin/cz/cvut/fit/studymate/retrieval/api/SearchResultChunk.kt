package cz.cvut.fit.studymate.retrieval.api

import java.util.UUID

data class SearchResultChunk(
    val chunkId: UUID,
    val materialId: UUID,
    val chunkIndex: Int,
    val text: String,
    val distance: Double,
)
