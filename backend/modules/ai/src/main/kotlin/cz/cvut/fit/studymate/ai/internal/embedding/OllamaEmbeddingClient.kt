package cz.cvut.fit.studymate.ai.internal.embedding

import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

internal class OllamaEmbeddingClient(
    restClientBuilder: RestClient.Builder,
    baseUrl: String,
    private val model: String,
    private val dimensions: Int,
) : EmbeddingClient {

    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    override suspend fun embed(texts: List<String>): List<FloatArray> = withContext(Dispatchers.IO) {
        try {
            val response = restClient.post()
                .uri("/api/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .body(EmbedRequest(model = model, input = texts))
                .retrieve()
                .body<EmbedResponse>()

            val embeddings = response?.embeddings
            if (embeddings.isNullOrEmpty() || embeddings.size != texts.size) {
                throw EmbeddingProviderException(
                    "Ollama returned ${embeddings?.size ?: 0} embeddings for ${texts.size} input texts",
                )
            }
            embeddings.map { it.toFloatArray() }
        } catch (e: RestClientException) {
            throw EmbeddingProviderException("Embedding request failed: ${e.message}", e)
        }
    }

    override fun dimensions(): Int = dimensions
}

private data class EmbedRequest(
    val model: String,
    val input: List<String>,
)

private data class EmbedResponse(
    val embeddings: List<List<Float>> = emptyList(),
)
