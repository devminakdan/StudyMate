package cz.cvut.fit.studymate.ai.internal.llm

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
internal class LlmClientsConfig {

    @Bean
    fun groqLlmClient(
        restClientBuilder: RestClient.Builder,
        @Value("\${studymate.ai.groq.base-url}") baseUrl: String,
        @Value("\${studymate.ai.groq.api-key}") apiKey: String,
        @Value("\${studymate.ai.groq.default-model}") defaultModel: String,
    ): OpenAiStyleLlmClient =
        OpenAiStyleLlmClient(restClientBuilder, baseUrl, apiKey.ifBlank { null }, defaultModel)

    @Bean
    fun ollamaLlmClient(
        restClientBuilder: RestClient.Builder,
        @Value("\${studymate.ai.ollama.base-url}") baseUrl: String,
        @Value("\${studymate.ai.ollama.default-model}") defaultModel: String,
    ): OpenAiStyleLlmClient =
        OpenAiStyleLlmClient(restClientBuilder, baseUrl, null, defaultModel)



    @Bean
    fun anthropicLlmClient(
        @Value("\${studymate.anthropic.api-key}") apiKey: String,
        @Value("\${studymate.anthropic.default-model}") defaultModel: String,
    ): AnthropicLlmClient = AnthropicLlmClient(apiKey, defaultModel)
}
