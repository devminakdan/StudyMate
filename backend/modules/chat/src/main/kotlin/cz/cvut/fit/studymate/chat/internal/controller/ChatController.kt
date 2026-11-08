package cz.cvut.fit.studymate.chat.internal.controller

import cz.cvut.fit.studymate.chat.internal.dto.ChatRequest
import cz.cvut.fit.studymate.chat.internal.dto.ChatResponse
import cz.cvut.fit.studymate.chat.internal.service.ChatService
import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Chat", description = "Answers grounded in the current user's course materials")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/chat")
internal class ChatController(
    private val chatService: ChatService,
) {

    @Operation(
        summary = "Ask a question about course materials",
        description = "Retrieves up to five source fragments in a course owned by the current user and generates one complete answer. " +
            "Returns an explanatory answer with empty sources without calling the LLM when no fragments are found. " +
            "Sources are the fragments supplied to the LLM, labeled S1, S2, and so on; page numbers are not available. " +
            "This operation does not save conversation history.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Complete answer and source fragments",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ChatResponse::class))],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Blank question, invalid JSON body, or invalid course UUID",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "403",
            description = "Missing or invalid access_token cookie, or the caller does not own the course. " +
                "Ownership errors contain ErrorResponse; authentication failures may have no response body.",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "404",
            description = "No course exists with the given id",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "502",
            description = "The embedding or LLM provider failed or returned an invalid answer",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping(
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun ask(
        @Parameter(description = "Course id", required = true) @PathVariable("courseId") courseId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: ChatRequest,
    ): ChatResponse =
        chatService.ask(
            courseId = courseId,
            userId = user.id,
            question = request.question,
        )
}
