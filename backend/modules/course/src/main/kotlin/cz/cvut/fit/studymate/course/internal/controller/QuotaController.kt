package cz.cvut.fit.studymate.course.internal.controller

import cz.cvut.fit.studymate.course.internal.dto.QuotaResponse
import cz.cvut.fit.studymate.course.internal.dto.StorageOverviewResponse
import cz.cvut.fit.studymate.course.internal.service.QuotaService
import cz.cvut.fit.studymate.course.internal.service.StorageOverviewService
import cz.cvut.fit.studymate.course.internal.service.StorageQuota
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Tag(name = "Quota", description = "The current user's storage usage and limit")
@RestController
@RequestMapping("/api/v1/users/me")
internal class QuotaController(
    private val quotaService: QuotaService,
    private val storageOverviewService: StorageOverviewService,
) {
    @Operation(
        summary = "Get the current user's storage quota",
        description = "Returns how many bytes the current user has used across all their courses' materials, and the free-tier limit.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Current usage and limit", content = [Content(schema = Schema(implementation = QuotaResponse::class))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or it's missing/invalid/unparseable", content = [Content()]),
    )
    @GetMapping("/quota")
    fun getQuota(@AuthenticationPrincipal user: AuthenticatedUser): QuotaResponse =
        QuotaResponse(
            usedBytes = quotaService.getUserStorageUsage(user.id),
            limitBytes = StorageQuota.FREE_TIER_BYTES,
        )

    @Operation(
        summary = "Get the current user's storage overview",
        description = "Returns every course owned by the current user with its storage footprint and materials, sorted so the least recently used courses come first.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Storage overview", content = [Content(schema = Schema(implementation = StorageOverviewResponse::class))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or it's missing/invalid/unparseable", content = [Content()]),
    )
    @GetMapping("/storage-overview")
    fun getStorageOverview(@AuthenticationPrincipal user: AuthenticatedUser): StorageOverviewResponse =
        storageOverviewService.getStorageOverview(user.id)
}
