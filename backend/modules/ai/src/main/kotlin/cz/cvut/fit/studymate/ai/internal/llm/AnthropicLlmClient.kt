package cz.cvut.fit.studymate.ai.internal.llm

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.MessageCreateParams
import cz.cvut.fit.studymate.ai.api.ChatMessage
import cz.cvut.fit.studymate.ai.api.CompletionOptions
import cz.cvut.fit.studymate.ai.api.LlmClient
import cz.cvut.fit.studymate.ai.api.LlmProviderException
import cz.cvut.fit.studymate.ai.api.MessageRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class AnthropicLlmClient(
    apiKey: String,
    private val defaultModel: String,
) : LlmClient {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    override suspend fun complete(messages: List<ChatMessage>, options: CompletionOptions): String =
        withContext(Dispatchers.IO) {
            val params = MessageCreateParams.builder()
                .model(options.model ?: defaultModel)
                .maxTokens(options.maxTokens.toLong())

            messages.firstOrNull { it.role == MessageRole.SYSTEM }
                ?.let { params.system(it.content) }

            messages.filter { it.role != MessageRole.SYSTEM }.forEach { message ->
                when (message.role) {
                    MessageRole.USER -> params.addUserMessage(message.content)
                    MessageRole.ASSISTANT -> params.addAssistantMessage(message.content)
                    MessageRole.SYSTEM -> Unit
                }
            }

            try {
                val response = client.messages().create(params.build())
                response.content().firstOrNull()
                    ?.text()
                    ?.orElse(null)
                    ?.text()
                    ?.takeIf { it.isNotBlank() }
                    ?: throw LlmProviderException("Empty response from Anthropic")
            } catch (e: AnthropicServiceException) {
                throw LlmProviderException("Anthropic request failed: ${e.message}", e)
            } catch (e: AnthropicIoException) {
                throw LlmProviderException("Anthropic request failed: ${e.message}", e)
            }
        }
}
