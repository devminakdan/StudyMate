package cz.cvut.fit.studymate.ai.internal.llm

import com.anthropic.errors.AnthropicIoException
import com.anthropic.errors.InternalServerException
import com.anthropic.errors.RateLimitException
import cz.cvut.fit.studymate.ai.api.ChatMessage
import cz.cvut.fit.studymate.ai.api.CompletionOptions
import cz.cvut.fit.studymate.ai.api.LlmClient
import cz.cvut.fit.studymate.ai.api.LlmProviderException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

internal class RetryingLlmClient(
    private val delegate: LlmClient,
    private val maxAttempts: Int = 3,
    private val initialBackoffMillis: Long = 500,
) : LlmClient {

    private val log = LoggerFactory.getLogger(RetryingLlmClient::class.java)

    override suspend fun complete(messages: List<ChatMessage>, options: CompletionOptions): String {
        var lastError: LlmProviderException? = null

        for (attempt in 1..maxAttempts) {
            try {
                return delegate.complete(messages, options)
            } catch (e: LlmProviderException) {
                lastError = e
                if (!isTransient(e) || attempt == maxAttempts) throw e

                val backoffMillis = initialBackoffMillis * (1L shl (attempt - 1))
                val delayMillis = backoffMillis + Random.nextLong(initialBackoffMillis)
                log.warn(
                    "Transient LLM provider error on attempt {}/{}, retrying in {}ms: {}",
                    attempt,
                    maxAttempts,
                    delayMillis,
                    e.message,
                )
                delay(delayMillis.milliseconds)
            }
        }

        throw lastError ?: LlmProviderException("LLM request failed after $maxAttempts attempts")
    }

    private fun isTransient(e: LlmProviderException): Boolean = when (val cause = e.cause) {
        is HttpServerErrorException -> true
        is HttpClientErrorException -> cause.statusCode.value() == 429
        is ResourceAccessException -> true
        is RateLimitException -> true
        is InternalServerException -> true
        is AnthropicIoException -> true
        else -> false
    }
}
