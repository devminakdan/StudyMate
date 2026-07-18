package cz.cvut.fit.studymate.ai.api

data class CompletionOptions(
    val model: String? = null,
    val maxTokens: Int = 1024,
    val temperature: Double = 0.3,
)
