package cz.cvut.fit.studymate.iam.internal.controller

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.dto.ChangeRoleRequest
import cz.cvut.fit.studymate.iam.internal.dto.UserResponse
import cz.cvut.fit.studymate.iam.internal.dto.toResponse
import cz.cvut.fit.studymate.iam.internal.service.UserService
import jakarta.validation.Valid
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
internal class UserController(
    private val service: UserService
) {
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal principal: AuthenticatedUser): UserResponse {
        return service.findById(principal.id)!!.toResponse()
    }

    @PatchMapping("/api/v1/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN')")
    fun changeRole(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ChangeRoleRequest,
    ): UserResponse {
        return service.changeRole(id, req.role).toResponse()
    }
}
