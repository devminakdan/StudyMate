package cz.cvut.fit.studymate.chat.internal.controller

import cz.cvut.fit.studymate.ai.api.ChatMessage
import cz.cvut.fit.studymate.ai.api.ContextChunk
import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import cz.cvut.fit.studymate.ai.api.LlmClient
import cz.cvut.fit.studymate.ai.api.LlmClientFactory
import cz.cvut.fit.studymate.ai.api.LlmProviderException
import cz.cvut.fit.studymate.ai.api.RagChatPrompts
import cz.cvut.fit.studymate.chat.internal.dto.ChatResponse
import cz.cvut.fit.studymate.chat.internal.dto.ChatSource
import cz.cvut.fit.studymate.chat.internal.service.ChatService
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
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
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.MethodParameter
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import java.util.UUID

internal class ChatControllerTest {

    private val service = mockk<ChatService>()
    private val retrievalLookup = mockk<RetrievalLookup>()
    private val factory = mockk<LlmClientFactory>()
    private val llmClient = mockk<LlmClient>()
    private val user = AuthenticatedUser(UUID.randomUUID(), "test@example.com", Role.USER)
    private val courseId = UUID.randomUUID()
    private val path = "/api/v1/courses/$courseId/chat"

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(ChatController(service))
        .setControllerAdvice(ChatExceptionHandler())
        .setCustomArgumentResolvers(object : HandlerMethodArgumentResolver {
            override fun supportsParameter(parameter: MethodParameter): Boolean =
                parameter.parameterType == AuthenticatedUser::class.java

            override fun resolveArgument(
                parameter: MethodParameter,
                mavContainer: ModelAndViewContainer?,
                webRequest: NativeWebRequest,
                binderFactory: WebDataBinderFactory?,
            ): Any = user
        })
        .build()

    private fun assertChatError(exception: RuntimeException, expectedStatus: Int, message: String) {
        coEvery { service.ask(any(), any(), any()) } throws exception

        val initial = mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":"Question"}"""),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().`is`(expectedStatus))
            .andExpect(jsonPath("$.message").value(message))
    }

    @Test
    fun `valid question delegates to service and returns answer with source fragments`() {
        val question = "What is atomicity?"
        val source = ChatSource("S1", UUID.randomUUID(), UUID.randomUUID(), 7, "All or nothing.")
        coEvery { service.ask(courseId, user.id, question) } returns
            ChatResponse("A transaction executes entirely or not at all [S1].", listOf(source))

        val initial = mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":"$question"}"""),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value("A transaction executes entirely or not at all [S1]."))
            .andExpect(jsonPath("$.sources.length()").value(1))
            .andExpect(jsonPath("$.sources[0].source").value("S1"))
            .andExpect(jsonPath("$.sources[0].chunkId").value(source.chunkId.toString()))
            .andExpect(jsonPath("$.sources[0].materialId").value(source.materialId.toString()))
            .andExpect(jsonPath("$.sources[0].chunkIndex").value(7))
            .andExpect(jsonPath("$.sources[0].text").value(source.text))
            .andExpect(jsonPath("$.sources[0].pageNumber").doesNotExist())

        coVerify(exactly = 1) { service.ask(courseId, user.id, question) }
    }

    @Test
    fun `HTTP question reaches real service and LLM and returns matching source labels and metadata`() {
        val question = "What is atomicity?"
        val first = SearchResultChunk(
            chunkId = UUID.randomUUID(),
            materialId = UUID.randomUUID(),
            chunkIndex = 7,
            text = "All or nothing.",
            distance = 0.05,
        )
        val second = SearchResultChunk(
            chunkId = UUID.randomUUID(),
            materialId = UUID.randomUUID(),
            chunkIndex = 2,
            text = "Failures roll back changes.",
            distance = 0.15,
        )
        val messages = slot<List<ChatMessage>>()
        val answer = "A transaction executes entirely or not at all [S1]; failures roll back changes [S2]."

        every { factory.getClient() } returns llmClient
        val realService = ChatService(retrievalLookup, factory)
        val realMvc = MockMvcBuilders
            .standaloneSetup(ChatController(realService))
            .setControllerAdvice(ChatExceptionHandler())
            .setCustomArgumentResolvers(object : HandlerMethodArgumentResolver {
                override fun supportsParameter(parameter: MethodParameter): Boolean =
                    parameter.parameterType == AuthenticatedUser::class.java

                override fun resolveArgument(
                    parameter: MethodParameter,
                    mavContainer: ModelAndViewContainer?,
                    webRequest: NativeWebRequest,
                    binderFactory: WebDataBinderFactory?,
                ): Any = user
            })
            .build()
        coEvery {
            retrievalLookup.searchCourse(courseId, user.id, question, limit = 5)
        } returns listOf(first, second)
        coEvery { llmClient.complete(capture(messages), any()) } returns answer

        val initial = realMvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":"$question"}"""),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        realMvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value(answer))
            .andExpect(jsonPath("$.sources.length()").value(2))
            .andExpect(jsonPath("$.sources[0].source").value("S1"))
            .andExpect(jsonPath("$.sources[0].chunkId").value(first.chunkId.toString()))
            .andExpect(jsonPath("$.sources[0].materialId").value(first.materialId.toString()))
            .andExpect(jsonPath("$.sources[0].chunkIndex").value(7))
            .andExpect(jsonPath("$.sources[0].text").value(first.text))
            .andExpect(jsonPath("$.sources[1].source").value("S2"))
            .andExpect(jsonPath("$.sources[1].chunkId").value(second.chunkId.toString()))
            .andExpect(jsonPath("$.sources[1].materialId").value(second.materialId.toString()))
            .andExpect(jsonPath("$.sources[1].chunkIndex").value(2))
            .andExpect(jsonPath("$.sources[1].text").value(second.text))
            .andExpect(jsonPath("$.sources[0].pageNumber").doesNotExist())

        assertThat(messages.captured).isEqualTo(
            RagChatPrompts.buildMessages(
                listOf(ContextChunk("S1", first.text), ContextChunk("S2", second.text)),
                question,
            ),
        )
        coVerify(exactly = 1) { retrievalLookup.searchCourse(courseId, user.id, question, limit = 5) }
        coVerify(exactly = 1) { llmClient.complete(any(), any()) }
    }

    @Test
    fun `empty retrieval response is returned as a complete JSON answer with empty sources`() {
        val question = "What is atomicity?"
        val answer = "Not found an answer for course: $courseId, question: $question"
        coEvery { service.ask(courseId, user.id, question) } returns ChatResponse(answer, emptyList())

        val initial = mvc.perform(
            post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":"$question"}"""),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.answer").value(answer))
            .andExpect(jsonPath("$.sources").isEmpty)
    }

    @Test
    fun `invalid service request returns 400 with ErrorResponse`() {
        assertChatError(InvalidRetrievalRequestException("Invalid search query"), 400, "Invalid search query")
    }

    @Test
    fun `foreign course returns 403 with ErrorResponse`() {
        assertChatError(RetrievalAccessDeniedException(courseId), 403, "Access to course $courseId is denied")
    }

    @Test
    fun `missing course returns 404 with ErrorResponse`() {
        assertChatError(RetrievalCourseNotFoundException(courseId), 404, "Course not found: $courseId")
    }

    @Test
    fun `embedding failure returns 502 without exposing provider details`() {
        assertChatError(EmbeddingProviderException("Internal Ollama address and error"), 502, "Embedding generation failed")
    }

    @Test
    fun `LLM failure returns 502 without exposing provider details`() {
        assertChatError(LlmProviderException("Provider API key and internal error"), 502, "Answer generation failed")
    }

    @Test
    fun `empty or blank question returns 400 before service invocation`() {
        for (question in listOf("", "   ")) {
            mvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":"$question"}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("question: Chat question must not be blank"))
        }
        verify { service wasNot Called }
    }

    @Test
    fun `invalid JSON or missing question returns 400 before service invocation`() {
        for (body in listOf("", "{", "{}", """{"question":null}""", """{"question":{}}""")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Invalid JSON body: provide a string question"))
        }
        verify { service wasNot Called }
    }

    @Test
    fun `non string question returns 400 before service invocation`() {
        coEvery { service.ask(any(), any(), any()) } returns ChatResponse("Answer", emptyList())

        for (value in listOf("42", "42.5", "true", "false", "[]")) {
            mvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":$value}"""),
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Invalid JSON body: provide a string question"))
        }
        verify { service wasNot Called }
    }

    @Test
    fun `numeric and boolean text remain valid string questions`() {
        for (question in listOf("42", "true", "false")) {
            coEvery { service.ask(courseId, user.id, question) } returns ChatResponse("Answer", emptyList())

            val initial = mvc.perform(
                post(path).contentType(MediaType.APPLICATION_JSON).content("""{"question":"$question"}"""),
            )
                .andExpect(request().asyncStarted())
                .andReturn()

            mvc.perform(asyncDispatch(initial))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.answer").value("Answer"))
            coVerify(exactly = 1) { service.ask(courseId, user.id, question) }
        }
    }

    @Test
    fun `invalid course UUID returns 400 before service invocation`() {
        mvc.perform(
            post("/api/v1/courses/bad-uuid/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"Question"}"""),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Invalid value for parameter 'courseId'"))

        verify { service wasNot Called }
    }

    @Test
    fun `request body cannot override the authenticated principal user id`() {
        val otherUserId = UUID.randomUUID()
        coEvery { service.ask(courseId, user.id, "Question") } returns ChatResponse("Answer", emptyList())

        val initial = mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"question":"Question","userId":"$otherUserId"}"""),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(initial)).andExpect(status().isOk)
        coVerify(exactly = 1) { service.ask(courseId, user.id, "Question") }
        coVerify(exactly = 0) { service.ask(any(), otherUserId, any()) }
    }

    @Test
    fun `non JSON request returns 415 before service invocation`() {
        mvc.perform(post(path).contentType(MediaType.TEXT_PLAIN).content("Question"))
            .andExpect(status().isUnsupportedMediaType)

        verify { service wasNot Called }
    }
}
