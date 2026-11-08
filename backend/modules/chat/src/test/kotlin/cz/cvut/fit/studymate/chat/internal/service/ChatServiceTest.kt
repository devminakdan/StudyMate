package cz.cvut.fit.studymate.chat.internal.service

import cz.cvut.fit.studymate.ai.api.ContextChunk
import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import cz.cvut.fit.studymate.ai.api.LlmClient
import cz.cvut.fit.studymate.ai.api.LlmClientFactory
import cz.cvut.fit.studymate.ai.api.LlmProviderException
import cz.cvut.fit.studymate.ai.api.RagChatPrompts
import cz.cvut.fit.studymate.chat.internal.dto.ChatSource
import cz.cvut.fit.studymate.retrieval.api.RetrievalLookup
import cz.cvut.fit.studymate.retrieval.api.SearchResultChunk
import cz.cvut.fit.studymate.retrieval.internal.exceptions.InvalidRetrievalRequestException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalAccessDeniedException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalCourseNotFoundException
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

internal class ChatServiceTest {

    private val retrievalLookup = mockk<RetrievalLookup>()
    private val factory = mockk<LlmClientFactory>()
    private val llmClient = mockk<LlmClient>()
    private lateinit var service: ChatService

    @BeforeEach
    fun setUp() {
        every { factory.getClient() } returns llmClient
        service = ChatService(retrievalLookup, factory)
    }

    private fun searchResultChunk(
        chunkId: UUID = UUID.randomUUID(),
        materialId: UUID = UUID.randomUUID(),
        chunkIndex: Int = 0,
        text: String = "Chunk text",
        distance: Double = 0.05,
    ) = SearchResultChunk(chunkId, materialId, chunkIndex, text, distance)

    @Test
    fun `ask returns no context answer and empty sources without calling LLM when retrieval is empty`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val question = "What is atomicity?"

        coEvery {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        } returns emptyList()

        val response = service.ask(courseId, userId, question)

        assertThat(response.answer).isEqualTo("Not found an answer for course: $courseId, question: $question")
        assertThat(response.sources).isEmpty()
        coVerify(exactly = 1) {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        }
        verify { llmClient wasNot Called }
    }

    @Test
    fun `ask generates answer from retrieved chunks and returns matching sources in retrieval order`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val question = "What is atomicity?"
        val first = searchResultChunk(chunkIndex = 7, text = "A transaction executes entirely or not at all.")
        val second = searchResultChunk(chunkIndex = 2, text = "A failed transaction rolls back its changes.", distance = 0.15)
        val messages = RagChatPrompts.buildMessages(
            chunks = listOf(ContextChunk("S1", first.text), ContextChunk("S2", second.text)),
            question = question,
        )
        val answer = "A transaction executes entirely or not at all [S1]; a failed transaction rolls back [S2]."

        coEvery {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        } returns listOf(first, second)
        coEvery { llmClient.complete(messages, any()) } returns answer

        val response = service.ask(courseId, userId, question)

        assertThat(response.answer).isEqualTo(answer)
        assertThat(response.sources).containsExactly(
            ChatSource("S1", first.chunkId, first.materialId, first.chunkIndex, first.text),
            ChatSource("S2", second.chunkId, second.materialId, second.chunkIndex, second.text),
        )
        coVerify(exactly = 1) {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        }
        coVerify(exactly = 1) { llmClient.complete(messages, any()) }
    }

    @Test
    fun `ask propagates retrieval failures without calling LLM`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val question = "What is atomicity?"
        val failures = listOf(
            InvalidRetrievalRequestException("Invalid search query"),
            RetrievalCourseNotFoundException(courseId),
            RetrievalAccessDeniedException(courseId),
            EmbeddingProviderException("Embedding provider unavailable"),
        )

        for (failure in failures) {
            coEvery {
                retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
            } throws failure

            val exception = assertThrows<RuntimeException> {
                service.ask(courseId, userId, question)
            }

            assertThat(exception).isSameAs(failure)
        }

        coVerify(exactly = failures.size) {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        }
        verify { llmClient wasNot Called }
    }

    @Test
    fun `ask propagates LLM provider failure instead of returning an answer`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val question = "What is atomicity?"
        val failure = LlmProviderException("LLM provider unavailable")

        coEvery {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        } returns listOf(searchResultChunk())
        coEvery { llmClient.complete(any(), any()) } throws failure

        val exception = assertThrows<LlmProviderException> {
            service.ask(courseId, userId, question)
        }

        assertThat(exception).isSameAs(failure)
        coVerify(exactly = 1) { llmClient.complete(any(), any()) }
    }

    @Test
    fun `ask rejects empty or blank LLM answers`() = runBlocking {
        val courseId = UUID.randomUUID()
        val userId = UUID.randomUUID()
        val question = "What is atomicity?"

        coEvery {
            retrievalLookup.searchCourse(courseId, userId, question, limit = 5)
        } returns listOf(searchResultChunk())

        for (answer in listOf("", " \n\t")) {
            coEvery { llmClient.complete(any(), any()) } returns answer

            val exception = assertThrows<LlmProviderException> {
                service.ask(courseId, userId, question)
            }

            assertThat(exception.message).isEqualTo("Empty response from LLM provider")
        }

        coVerify(exactly = 2) { llmClient.complete(any(), any()) }
    }
}
