package cz.cvut.fit.studymate.ai.api

interface EmbeddingClient {
    suspend fun embed(texts: List<String>): List<FloatArray>
    fun dimensions(): Int
}
