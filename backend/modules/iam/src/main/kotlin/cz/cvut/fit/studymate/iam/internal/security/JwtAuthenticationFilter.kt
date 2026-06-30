package cz.cvut.fit.studymate.iam.internal.security

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.service.JwtService
import cz.cvut.fit.studymate.iam.internal.service.TokenType
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
internal class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val jwtCookies: JwtCookies,
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = jwtCookies.extractAccessToken(request)

        if (token != null) {
            try {
                val claims = jwtService.parseAndValidate(token)

                if (claims.type != TokenType.ACCESS) {
                    log.debug("Wrong token type in access cookie: {}", claims.type)
                } else {
                    val authUser = AuthenticatedUser(claims.userId, claims.email, claims.role)
                    val authorities = setOf(claims.role).map { SimpleGrantedAuthority(it.asAuthority()) }
                    val auth = UsernamePasswordAuthenticationToken(authUser, null, authorities)
                    SecurityContextHolder.getContext().authentication = auth
                }
            } catch (e: Exception) {
                log.debug("Invalid JWT token: ${e.message}")
            }
        }

        filterChain.doFilter(request, response)
    }
}
