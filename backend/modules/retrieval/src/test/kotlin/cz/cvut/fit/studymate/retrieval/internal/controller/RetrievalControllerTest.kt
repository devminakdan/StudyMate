package cz.cvut.fit.studymate.retrieval.internal.controller

import cz.cvut.fit.studymate.ai.api.EmbeddingProviderException
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.api.Role
import cz.cvut.fit.studymate.retrieval.api.RetrievalLookup
import cz.cvut.fit.studymate.retrieval.api.SearchResultChunk
import cz.cvut.fit.studymate.retrieval.internal.exceptions.InvalidRetrievalRequestException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalAccessDeniedException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalCourseNotFoundException
import cz.cvut.fit.studymate.retrieval.internal.exceptions.RetrievalMaterialNotFoundException
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
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

internal class RetrievalControllerTest {

    private val lookup = mockk<RetrievalLookup>()
    private val user = AuthenticatedUser(UUID.randomUUID(), "test@example.com", Role.USER)
    private val courseId = UUID.randomUUID()
    private val path = "/api/v1/courses/$courseId/search"
    private val materialId = UUID.randomUUID()
    private val materialPath = "/api/v1/courses/$courseId/materials/$materialId/search"

    private val mvc: MockMvc = MockMvcBuilders
        .standaloneSetup(RetrievalController(lookup))
        .setControllerAdvice(RetrievalExceptionHandler())
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

    private fun assertSearchError(exception: RuntimeException, expectedStatus: Int, message: String) {
        coEvery { lookup.searchCourse(any(), any(), any(), any()) } throws exception
        coEvery { lookup.searchMaterial(any(), any(), any(), any(), any()) } throws exception

        for (endpoint in listOf(path, materialPath)) {
            val initial = mvc.perform(
                post(endpoint).contentType(MediaType.APPLICATION_JSON).content("""{"query":"Question"}"""),
            )
                .andExpect(request().asyncStarted())
                .andReturn()

            mvc.perform(asyncDispatch(initial))
                .andExpect(status().`is`(expectedStatus))
                .andExpect(jsonPath("$.message").value(message))
        }
    }

    @Test
    fun `invalid service request returns 400 with ErrorResponse for both searches`() {
        assertSearchError(InvalidRetrievalRequestException("Invalid search query"), 400, "Invalid search query")
    }

    @Test
    fun `foreign course returns 403 with ErrorResponse for both searches`() {
        assertSearchError(RetrievalAccessDeniedException(courseId), 403, "Access to course $courseId is denied")
    }

    @Test
    fun `missing course returns 404 with ErrorResponse for both searches`() {
        assertSearchError(RetrievalCourseNotFoundException(courseId), 404, "Course not found: $courseId")
    }

    @Test
    fun `missing material returns 404 with ErrorResponse`() {
        coEvery { lookup.searchMaterial(any(), any(), any(), any(), any()) } throws RetrievalMaterialNotFoundException(materialId)
        val initial = mvc.perform(
            post(materialPath).contentType(MediaType.APPLICATION_JSON).content("""{"query":"Question"}"""),
        ).andReturn()

        mvc.perform(asyncDispatch(initial))
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.message").value("Material $materialId was not found"))
    }

    @Test
    fun `embedding failure returns 502 without exposing provider details for both searches`() {
        assertSearchError(EmbeddingProviderException("Internal Ollama address and error"), 502, "Embedding generation failed")
    }

    @Test
    fun `invalid course or material UUID returns 400 before service invocation`() {
        for ((endpoint, parameter) in listOf(
            "/api/v1/courses/bad-uuid/search" to "courseId",
            "/api/v1/courses/$courseId/materials/bad-uuid/search" to "materialId",
        )) {
            mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("""{"query":"Question"}"""))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Invalid value for parameter '$parameter'"))
        }
        verify { lookup wasNot Called }
    }

    @Test
    fun `nonpositive limits are rejected independently of query validation for both searches`() {
        for (endpoint in listOf(path, materialPath)) {
            for (limit in listOf(0, -1)) {
                mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("""{"query":"Question","limit":$limit}"""))
                    .andExpect(status().isBadRequest)
                    .andExpect(jsonPath("$.message").value("limit: Search limit must be positive"))
            }
        }
        verify { lookup wasNot Called }
    }

    @Test
    fun `malformed JSON returns 400 with ErrorResponse for both searches`() {
        for (endpoint in listOf(path, materialPath)) {
            mvc.perform(post(endpoint).contentType(MediaType.APPLICATION_JSON).content("{")
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.message").value("Invalid JSON body: provide a string query and an optional integer limit"))
        }
        verify { lookup wasNot Called }
    }

    @Test
    fun `blank queries and nonpositive limits fail DTO validation before service invocation`() {
        val body = """{"query": "   ", "limit": 0}"""

        mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Search query must not be blank")))

        verify { lookup wasNot Called }
    }

    @Test
    fun `malformed or missing query stays a JSON decoding error with 400`() {
        val body = """{"limit": 5}"""

        mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(status().isBadRequest)

        verify { lookup wasNot Called }
    }

    @Test
    fun `valid request still gets default limit and successful JSON array`() {
        val chunk = SearchResultChunk(
            chunkId = UUID.randomUUID(),
            materialId = UUID.randomUUID(),
            chunkIndex = 0,
            text = "Atomicity ensures all-or-nothing execution",
            distance = 0.05,
        )

        coEvery {
            lookup.searchCourse(courseId, user.id, "What is atomicity?", limit = 5)
        } returns listOf(chunk)

        val body = """{"query": "What is atomicity?"}"""

        val mvcResult = mvc.perform(
            post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].chunkId").value(chunk.chunkId.toString()))
            .andExpect(jsonPath("$[0].materialId").value(chunk.materialId.toString()))
            .andExpect(jsonPath("$[0].chunkIndex").value(0))
            .andExpect(jsonPath("$[0].text").value("Atomicity ensures all-or-nothing execution"))
            .andExpect(jsonPath("$[0].distance").value(0.05))

        coVerify(exactly = 1) {
            lookup.searchCourse(courseId, user.id, "What is atomicity?", limit = 5)
        }
    }

    @Test
    fun `searchMaterial delegates to lookup and returns JSON array`() {
        val materialId = UUID.randomUUID()
        val chunk = SearchResultChunk(
            chunkId = UUID.randomUUID(),
            materialId = materialId,
            chunkIndex = 1,
            text = "Specific slide content",
            distance = 0.08,
        )

        coEvery {
            lookup.searchMaterial(materialId, courseId, user.id, "B-Tree node size", limit = 10)
        } returns listOf(chunk)

        val materialPath = "/api/v1/courses/$courseId/materials/$materialId/search"
        val body = """{"query": "B-Tree node size", "limit": 10}"""

        val mvcResult = mvc.perform(
            post(materialPath)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body),
        )
            .andExpect(request().asyncStarted())
            .andReturn()

        mvc.perform(asyncDispatch(mvcResult))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].chunkId").value(chunk.chunkId.toString()))
            .andExpect(jsonPath("$[0].materialId").value(materialId.toString()))
            .andExpect(jsonPath("$[0].chunkIndex").value(1))
            .andExpect(jsonPath("$[0].text").value("Specific slide content"))
            .andExpect(jsonPath("$[0].distance").value(0.08))

        coVerify(exactly = 1) {
            lookup.searchMaterial(materialId, courseId, user.id, "B-Tree node size", limit = 10)
        }
    }
}
