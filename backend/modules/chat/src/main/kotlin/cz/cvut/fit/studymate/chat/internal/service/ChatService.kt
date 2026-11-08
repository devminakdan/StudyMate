package cz.cvut.fit.studymate.chat.internal.service

import cz.cvut.fit.studymate.ai.api.ContextChunk
import cz.cvut.fit.studymate.ai.api.LlmClient
import cz.cvut.fit.studymate.ai.api.LlmClientFactory
import cz.cvut.fit.studymate.ai.api.LlmProviderException
import cz.cvut.fit.studymate.ai.api.RagChatPrompts
import cz.cvut.fit.studymate.chat.internal.dto.ChatResponse
import cz.cvut.fit.studymate.chat.internal.dto.ChatSource
import cz.cvut.fit.studymate.retrieval.api.RetrievalLookup
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class ChatService(
    private val retrievalLookup: RetrievalLookup,
    llmClientFactory: LlmClientFactory,
) {
    private val llmClient: LlmClient = llmClientFactory.getClient()

    suspend fun ask(
        courseId: UUID,
        userId: UUID,
        question: String,
    ): ChatResponse {
        val chunks = retrievalLookup.searchCourse(
            courseId = courseId,
            userId = userId,
            query = question,
            limit = 5,
        )
        if (chunks.isEmpty()) {
            return ChatResponse(
                answer = "Not found an answer for course: $courseId, question: $question",
                sources = emptyList(),
            )
        }

        val sources = chunks.mapIndexed { index, chunk ->
            ChatSource(
                source = "S${index + 1}",
                chunkId = chunk.chunkId,
                materialId = chunk.materialId,
                chunkIndex = chunk.chunkIndex,
                text = chunk.text,
            )
        }
        val context = sources.map { source ->
            ContextChunk(source = source.source, content = source.text)
        }
        val messages = RagChatPrompts.buildMessages(chunks = context, question = question)
        val answer = llmClient.complete(messages)
        if (answer.isBlank()) {
            throw LlmProviderException("Empty response from LLM provider")
        }

        return ChatResponse(answer = answer, sources = sources)
    }
}
