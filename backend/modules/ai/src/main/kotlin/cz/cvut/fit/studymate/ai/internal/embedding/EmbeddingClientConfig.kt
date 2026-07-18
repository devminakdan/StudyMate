package cz.cvut.fit.studymate.ai.internal.embedding

import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
internal class EmbeddingClientConfig {

    @Bean
    fun ollamaEmbeddingClient(
        restClientBuilder: RestClient.Builder,
        @Value("\${studymate.ai.embedding.base-url}") baseUrl: String,
        @Value("\${studymate.ai.embedding.model}") model: String,
        @Value("\${studymate.ai.embedding.dimensions}") dimensions: Int,
    ): EmbeddingClient =
        RetryingEmbeddingClient(OllamaEmbeddingClient(restClientBuilder, baseUrl, model, dimensions))
}
