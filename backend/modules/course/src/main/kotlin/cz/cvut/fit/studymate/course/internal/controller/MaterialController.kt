package cz.cvut.fit.studymate.course.internal.controller

import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.course.internal.dto.MaterialResponse
import cz.cvut.fit.studymate.course.internal.dto.toResponse
import cz.cvut.fit.studymate.course.internal.service.MaterialService
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Tag(name = "Materials", description = "Upload and manage materials attached to a course")
@RestController
@RequestMapping("/api/v1/courses/{courseId}/materials")
internal class MaterialController(
    private val materialService: MaterialService
) {
    @Operation(
        summary = "Upload a material",
        description = "Validates and stores a PDF/PPTX file for a course, creates a PENDING material record, and publishes a Kafka event to start async processing.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "202", description = "Accepted, processing started", content = [Content(schema = Schema(implementation = MaterialResponse::class))]),
        ApiResponse(responseCode = "400", description = "File is empty, too large, or not a supported type", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or caller doesn't own this course", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No course exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun uploadMaterial(
        @Parameter(description = "Course id") @PathVariable courseId: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
        @RequestParam("file") file: MultipartFile,
    ): MaterialResponse =
        materialService.uploadMaterial(courseId, user.id, file).toResponse()

    @Operation(
        summary = "List materials in a course",
        description = "Paginated list of materials for a course, newest first. Total count is returned in the X-Total-Count header.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Page of materials", content = [Content(array = ArraySchema(schema = Schema(implementation = MaterialResponse::class)))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or caller doesn't own this course", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No course exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @GetMapping
    fun listMaterials(
        @Parameter(description = "Course id") @PathVariable courseId: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
        @Parameter(description = "Zero-based page index") @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "Page size, 1-100") @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<List<MaterialResponse>> {
        val materials = materialService.listMaterials(courseId, user.id, page.coerceAtLeast(0), size.coerceIn(1, 100))
        val total = materialService.countMaterials(courseId, user.id)
        return ResponseEntity.ok()
            .header("X-Total-Count", total.toString())
            .body(materials.map { it.toResponse() })
    }

    @Operation(
        summary = "Get a material by id",
        description = "Returns a single material. Only the course owner can access it.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Material found", content = [Content(schema = Schema(implementation = MaterialResponse::class))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or caller doesn't own this course", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No course or material exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @GetMapping("/{materialId}")
    fun getMaterial(
        @Parameter(description = "Course id") @PathVariable courseId: UUID,
        @Parameter(description = "Material id") @PathVariable materialId: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ): MaterialResponse =
        materialService.getMaterial(courseId, materialId, user.id).toResponse()

    @Operation(
        summary = "Delete a material",
        description = "Deletes a material's stored file and its metadata. Only the course owner can delete it.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "204", description = "Material deleted"),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or caller doesn't own this course", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No course or material exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @DeleteMapping("/{materialId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMaterial(
        @Parameter(description = "Course id") @PathVariable courseId: UUID,
        @Parameter(description = "Material id") @PathVariable materialId: UUID,
        @AuthenticationPrincipal user: AuthenticatedUser,
    ) {
        materialService.deleteMaterial(courseId, materialId, user.id)
    }
}
