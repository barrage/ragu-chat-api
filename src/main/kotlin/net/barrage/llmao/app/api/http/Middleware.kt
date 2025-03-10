package net.barrage.llmao.app.api.http

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URL
import java.util.concurrent.TimeUnit
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.tryUuid

/** Obtain the current user initiating the request. */
fun ApplicationCall.user(): User {
  val principal = request.call.principal<JWTPrincipal>()!!
  val user =
    User(
      id = tryUuid(principal.subject!!),
      email = principal.payload.getClaim("email").asString(),
      fullName = principal.payload.getClaim("given_name").asString(),
      active = true,
      role = Role.ADMIN,
      avatar = principal.payload.getClaim("avatar").asString(),
      createdAt = KOffsetDateTime.now(),
      updatedAt = KOffsetDateTime.now(),
      deletedAt = null,
      firstName = "TODO()",
      lastName = "",
    )
  return user
}

fun Application.authMiddleware(issuer: String, jwksEndpoint: String, leeway: Long) {
  val jwkProvider =
    JwkProviderBuilder(URL(jwksEndpoint))
      .cached(10, 24, TimeUnit.HOURS)
      .rateLimited(10, 1, TimeUnit.MINUTES)
      .build()

  install(Authentication) {
    jwt("admin") {
      verifier(jwkProvider, issuer) { acceptLeeway(leeway) }
      // This is a janky way to get the access token cookie from the frontend
      // and we should investigate how to handle this later
      authHeader {
        val token = it.request.cookies["access_token"]
        if (token != null) {
          HttpAuthHeader.Single(AuthScheme.Bearer, token)
        } else {
          null
        }
      }
      validate { credential ->
        val entitlements = credential.payload.getClaim("entitlements").asList(String::class.java)
        if (entitlements.contains("admin")) {
          JWTPrincipal(credential.payload)
        } else {
          null
        }
      }
    }

    jwt("user") {
      verifier(jwkProvider, issuer) { acceptLeeway(leeway) }
      // This is a janky way to get the access token cookie from the frontend
      // and we should investigate how to handle this later
      authHeader {
        val token = it.request.cookies["access_token"]
        if (token != null) {
          HttpAuthHeader.Single(AuthScheme.Bearer, token)
        } else {
          null
        }
      }
      validate { credential ->
        val entitlements = credential.payload.getClaim("entitlements").asList(String::class.java)
        for (entitlement in entitlements) {
          if (entitlement == "admin" || entitlement == "user") {
            return@validate JWTPrincipal(credential.payload)
          }
        }
        null
      }
    }
  }
}
