package cz.cvut.fit.studymate.retrieval.internal.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive

internal data class SearchRequest(
    @field:NotBlank(message = "Search query must not be blank")
    val query: String,

    @field:Positive(message = "Search limit must be positive")
    val limit: Int = 5,
)
