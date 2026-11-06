package cz.cvut.fit.studymate.retrieval.internal.controller

import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.retrieval.api.RetrievalLookup
import cz.cvut.fit.studymate.retrieval.api.SearchResultChunk
import cz.cvut.fit.studymate.retrieval.internal.dto.SearchRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
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

@Tag(name = "Retrieval", description = "Semantic search over the current user's course materials")
@RestController
@RequestMapping("/api/v1/courses/{courseId}")
internal class RetrievalController(
    private val retrievalLookup: RetrievalLookup,
) {

    @Operation(
        summary = "Search course materials",
        description = "Finds source fragments in a course owned by the current user. " +
            "Returns up to the requested limit, ordered by increasing cosine distance. " +
            "Returns an empty array if the course has no indexed fragments. " +
            "This operation returns source fragments, not a generated answer.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Source fragments ordered by increasing cosine distance",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = ArraySchema(schema = Schema(implementation = SearchResultChunk::class)),
            )],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Blank query, nonpositive limit, invalid JSON body, or invalid course UUID",
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
            description = "The embedding provider could not generate a valid embedding",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping(
        path = ["/search"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun searchCourse(
        @Parameter(description = "Course id", required = true) @PathVariable("courseId") courseId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: SearchRequest,
    ): List<SearchResultChunk> =
        retrievalLookup.searchCourse(
            courseId = courseId,
            userId = user.id,
            query = request.query,
            limit = request.limit,
        )

    @Operation(
        summary = "Search inside a specific material",
        description = "Finds source fragments inside a specific material in a course owned by the current user. " +
            "Returns up to the requested limit, ordered by increasing cosine distance.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(
            responseCode = "200",
            description = "Source fragments ordered by increasing cosine distance",
            content = [Content(
                mediaType = MediaType.APPLICATION_JSON_VALUE,
                array = ArraySchema(schema = Schema(implementation = SearchResultChunk::class)),
            )],
        ),
        ApiResponse(
            responseCode = "400",
            description = "Blank query, nonpositive limit, invalid JSON body, or invalid UUID",
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
            description = "Course or material does not exist",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
        ApiResponse(
            responseCode = "502",
            description = "The embedding provider could not generate a valid embedding",
            content = [Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = Schema(implementation = ErrorResponse::class))],
        ),
    )
    @PostMapping(
        path = ["/materials/{materialId}/search"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun searchMaterial(
        @Parameter(description = "Course id", required = true) @PathVariable("courseId") courseId: UUID,
        @Parameter(description = "Material id", required = true) @PathVariable("materialId") materialId: UUID,
        @Parameter(hidden = true) @AuthenticationPrincipal user: AuthenticatedUser,
        @Valid @RequestBody request: SearchRequest,
    ): List<SearchResultChunk> =
        retrievalLookup.searchMaterial(
            materialId = materialId,
            courseId = courseId,
            userId = user.id,
            query = request.query,
            limit = request.limit,
        )
}
