package cz.cvut.fit.studymate.iam.internal.controller

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.dto.UserResponse
import cz.cvut.fit.studymate.iam.internal.dto.toResponse
import cz.cvut.fit.studymate.iam.internal.service.UserService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
internal class UserController(
    private val service: UserService
) {
    @GetMapping("/me")
    fun getMe(@AuthenticationPrincipal principal: AuthenticatedUser): UserResponse {
        return service.findById(principal.id)!!.toResponse()
    }
}
