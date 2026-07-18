package cz.cvut.fit.studymate.ai.internal.llm

import cz.cvut.fit.studymate.ai.api.ChatMessage
import cz.cvut.fit.studymate.ai.api.CompletionOptions
import cz.cvut.fit.studymate.ai.api.LlmClient
import cz.cvut.fit.studymate.ai.api.LlmProviderException
import cz.cvut.fit.studymate.ai.api.MessageRole
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import org.springframework.web.client.body

internal class OpenAiStyleLlmClient(
    restClientBuilder: RestClient.Builder,
    baseUrl: String,
    private val apiKey: String?,
    private val defaultModel: String,
) : LlmClient {

    private val restClient = restClientBuilder.baseUrl(baseUrl).build()

    override suspend fun complete(messages: List<ChatMessage>, options: CompletionOptions): String =
        withContext(Dispatchers.IO) {
            val request = ChatCompletionRequest(
                model = options.model ?: defaultModel,
                messages = messages.map { ChatCompletionMessageDto(role = it.role.toWireRole(), content = it.content) },
                maxTokens = options.maxTokens,
                temperature = options.temperature,
            )

            val requestSpec = restClient.post()
                .uri("/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
            if (apiKey != null) {
                requestSpec.header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
            }

            try {
                val response = requestSpec.body(request)
                    .retrieve()
                    .body<ChatCompletionResponse>()

                response?.choices?.firstOrNull()?.message?.content
                    ?.takeIf { it.isNotBlank() }
                    ?: throw LlmProviderException("Empty response from LLM provider")
            } catch (e: RestClientException) {
                throw LlmProviderException("LLM request failed: ${e.message}", e)
            }
        }

    private fun MessageRole.toWireRole(): String = when (this) {
        MessageRole.SYSTEM -> "system"
        MessageRole.USER -> "user"
        MessageRole.ASSISTANT -> "assistant"
    }
}

private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatCompletionMessageDto>,
    @get:JsonProperty("max_tokens") val maxTokens: Int,
    val temperature: Double,
)

private data class ChatCompletionMessageDto(
    val role: String,
    val content: String,
)

private data class ChatCompletionResponse(
    val choices: List<ChatCompletionChoice> = emptyList(),
)

private data class ChatCompletionChoice(
    val message: ChatCompletionMessageDto,
)
