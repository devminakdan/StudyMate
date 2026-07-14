package cz.cvut.fit.studymate.iam.internal.security

import cz.cvut.fit.studymate.iam.api.AuthenticatedUser
import cz.cvut.fit.studymate.iam.internal.service.JwtService
import jakarta.servlet.http.Cookie

internal fun accessTokenCookie(jwtService: JwtService, user: AuthenticatedUser): Cookie =
    Cookie(JwtCookies.ACCESS_COOKIE, jwtService.generateTokenPair(user).accessToken)
