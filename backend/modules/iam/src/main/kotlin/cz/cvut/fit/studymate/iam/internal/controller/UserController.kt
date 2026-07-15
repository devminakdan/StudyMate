package cz.cvut.fit.studymate.iam.internal.controller

import cz.cvut.fit.studymate.common.ErrorResponse
import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.dto.ChangeRoleRequest
import cz.cvut.fit.studymate.iam.internal.dto.UserResponse
import cz.cvut.fit.studymate.iam.internal.dto.toResponse
import cz.cvut.fit.studymate.iam.internal.service.UserService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@Tag(name = "Users", description = "Current-user profile and admin role management")
@RestController
internal class UserController(
    private val service: UserService
) {
    @Operation(
        summary = "Get the current user",
        description = "Returns the profile of whoever the access_token cookie identifies. Any authenticated role — no role restriction.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Current user", content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or it's missing/invalid/unparseable", content = [Content()]),
        ApiResponse(responseCode = "404", description = "The user the token refers to no longer exists", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal principal: AuthenticatedUser): UserResponse {
        return service.findById(principal.id)!!.toResponse()
    }

    @Operation(
        summary = "Change a user's role",
        description = "Admin-only: promotes or demotes the target user to the given role.",
    )
    @SecurityRequirement(name = "accessTokenCookie")
    @ApiResponses(
        ApiResponse(responseCode = "200", description = "Role updated", content = [Content(schema = Schema(implementation = UserResponse::class))]),
        ApiResponse(responseCode = "400", description = "Missing/invalid role in the request body, or {id} isn't a valid UUID", content = [Content()]),
        ApiResponse(responseCode = "403", description = "No access_token cookie, or the caller is authenticated but isn't ADMIN", content = [Content()]),
        ApiResponse(responseCode = "404", description = "No user exists with the given id", content = [Content(schema = Schema(implementation = ErrorResponse::class))]),
    )
    @PatchMapping("/api/v1/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    fun changeRole(
        @Parameter(description = "Id of the user whose role is being changed") @PathVariable id: UUID,
        @Valid @RequestBody req: ChangeRoleRequest,
    ): UserResponse {
        return service.changeRole(id, req.role).toResponse()
    }
}
