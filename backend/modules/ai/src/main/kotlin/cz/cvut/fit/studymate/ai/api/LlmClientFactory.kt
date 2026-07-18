package cz.cvut.fit.studymate.ai.api

import cz.cvut.fit.studymate.ai.internal.llm.RetryingLlmClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class LlmClientFactory(
    @Qualifier("groqLlmClient") private val groq: LlmClient,
    @Qualifier("ollamaLlmClient") private val ollama: LlmClient,
    @Qualifier("anthropicLlmClient") private val anthropic: LlmClient,
    @Value("\${studymate.ai.llm.provider}") private val provider: String,
) {

    fun getClient(): LlmClient {
        val raw = when (provider) {
            "groq" -> groq
            "ollama" -> ollama
            "anthropic" -> anthropic
            else -> error("Unknown studymate.ai.llm.provider: $provider")
        }
        return RetryingLlmClient(raw)
    }
}
