package cz.cvut.fit.studymate.ai.internal.embedding

import cz.cvut.fit.studymate.ai.api.EmbeddingClient
import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.ResourceAccessException
import kotlin.random.Random

internal class RetryingEmbeddingClient(
    private val delegate: EmbeddingClient,
    private val maxAttempts: Int = 3,
    private val initialBackoffMillis: Long = 500,
) : EmbeddingClient {

    private val log = LoggerFactory.getLogger(RetryingEmbeddingClient::class.java)

    override suspend fun embed(texts: List<String>): List<FloatArray> {
        var lastError: EmbeddingProviderException? = null

        for (attempt in 1..maxAttempts) {
            try {
                return delegate.embed(texts)
            } catch (e: EmbeddingProviderException) {
                lastError = e
                if (!isTransient(e) || attempt == maxAttempts) throw e

                val backoffMillis = initialBackoffMillis * (1L shl (attempt - 1))
                val delayMillis = backoffMillis + Random.nextLong(initialBackoffMillis)
                log.warn(
                    "Transient embedding provider error on attempt {}/{}, retrying in {}ms: {}",
                    attempt,
                    maxAttempts,
                    delayMillis,
                    e.message,
                )
                delay(delayMillis)
            }
        }

        throw lastError ?: EmbeddingProviderException("Embedding request failed after $maxAttempts attempts")
    }

    override fun dimensions(): Int = delegate.dimensions()

    private fun isTransient(e: EmbeddingProviderException): Boolean = when (val cause = e.cause) {
        is HttpServerErrorException -> true
        is HttpClientErrorException -> cause.statusCode.value() == 429
        is ResourceAccessException -> true
        else -> false
    }
}
