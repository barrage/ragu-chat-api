package net.barrage.llmao.app.api.http

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.interfaces.Verification
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URL
import java.util.concurrent.TimeUnit
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.User

private enum class Entitlement {
  ADMIN,
  USER;

  companion object {
    fun admin(): String {
      return ADMIN.name.lowercase()
    }

    fun user(): String {
      return USER.name.lowercase()
    }
  }
}

/** Obtain the current user initiating the request. */
fun ApplicationCall.user(): User {
  val principal = request.call.principal<JWTPrincipal>()!!
  val user =
    User(
      id = principal.subject ?: throw AppError.api(ErrorReason.Authentication, "Missing subject"),
      email = principal.payload.getClaim("email").asString(),
      username = principal.payload.getClaim("given_name").asString(),
    )
  return user
}

fun Application.authMiddleware(
  issuer: String,
  jwksEndpoint: String,
  leeway: Long,
  entitlementsClaim: String,
  audience: String,
) {
  val jwkProvider =
    JwkProviderBuilder(URL(jwksEndpoint))
      .cached(10, 24, TimeUnit.HOURS)
      .rateLimited(10, 1, TimeUnit.MINUTES)
      .build()

  val verification: Verification.() -> Unit = {
    acceptLeeway(leeway)
    withAudience(audience)
  }

  // This is a janky way to get the access token cookie from the frontend
  // and we should investigate how to handle this later
  val tokenExtractor: (ApplicationCall) -> HttpAuthHeader? = { call ->
    call.request.cookies["access_token"]?.let { token ->
      HttpAuthHeader.Single(AuthScheme.Bearer, token)
    } ?: call.request.parseAuthorizationHeader()
  }

  install(Authentication) {
    jwt("admin") {
      verifier(jwkProvider, issuer, verification)
      authHeader(tokenExtractor)
      validate { credential ->
        val entitlements = credential.payload.getClaim(entitlementsClaim).asList(String::class.java)
        if (entitlements.contains(Entitlement.admin())) {
          JWTPrincipal(credential.payload)
        } else {
          null
        }
      }
    }

    jwt("user") {
      verifier(jwkProvider, issuer, verification)
      authHeader(tokenExtractor)
      validate { credential ->
        val entitlements = credential.payload.getClaim(entitlementsClaim).asList(String::class.java)
        for (entitlement in entitlements) {
          if (entitlement == Entitlement.admin() || entitlement == Entitlement.user()) {
            return@validate JWTPrincipal(credential.payload)
          }
        }
        null
      }
    }
  }
}
