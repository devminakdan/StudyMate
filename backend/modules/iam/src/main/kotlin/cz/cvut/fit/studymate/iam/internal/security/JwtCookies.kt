package cz.cvut.fit.studymate.iam.internal.security

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant

@Component
internal class JwtCookies(
    @Value("\${studymate.security.jwt.access-token-ttl}") private val accessTtl: java.time.Duration,
    @Value("\${studymate.security.jwt.refresh-token-ttl}") private val refreshTtl: java.time.Duration,
    @Value("\${studymate.security.jwt.cookie-secure}") private val secure: Boolean,
) {
    fun setTokens(response: HttpServletResponse, accessToken: String, refreshToken: String) {
        val now = Instant.now()
        response.addCookie(buildCookie(ACCESS_COOKIE, accessToken, accessTtl))
        response.addCookie(buildCookie(REFRESH_COOKIE, refreshToken, refreshTtl))
    }

    fun clearTokens(response: HttpServletResponse) {
        response.addCookie(expiredCookie(ACCESS_COOKIE))
        response.addCookie(expiredCookie(REFRESH_COOKIE))
    }

    fun extractAccessToken(request: HttpServletRequest): String? =
        extractCookie(request, ACCESS_COOKIE)

    fun extractRefreshToken(request: HttpServletRequest): String? =
        extractCookie(request, REFRESH_COOKIE)

    private fun buildCookie(name: String, value: String, ttl: java.time.Duration) = Cookie(name, value).apply {
        isHttpOnly = true
        this.secure = this@JwtCookies.secure
        path = "/"
        maxAge = ttl.seconds.toInt()
        setAttribute("SameSite", "Strict")
    }

    private fun expiredCookie(name: String) = Cookie(name, "").apply {
        isHttpOnly = true
        this.secure = this@JwtCookies.secure
        path = "/"
        maxAge = 0
    }

    private fun extractCookie(request: HttpServletRequest, name: String): String? =
        request.cookies?.firstOrNull { it.name == name }?.value

    companion object {
        const val ACCESS_COOKIE = "access_token"
        const val REFRESH_COOKIE = "refresh_token"
    }
}
