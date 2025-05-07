package net.barrage.llmao.app.http

import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.Verification
import io.ktor.http.auth.AuthScheme
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import java.net.URL
import java.time.Instant
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
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

/**
 * Used to hold the initial configuration for JWTs and the claims we need when extracting users via
 * [ApplicationCall.user] function.
 */
object AuthConfiguration {
  lateinit var entitlementsClaim: String
}

/** Obtain the current user initiating the request. */
fun ApplicationCall.user(): User {
  val principal = request.call.principal<JWTPrincipal>()!!

  val email = principal.payload.getClaim("email").asString()
  val username = principal.payload.getClaim("given_name").asString()

  //  if (username.isNullOrBlank()) {
  //    throw AppError.api(ErrorReason.Authentication, "Missing given_name claim")
  //  }

  if (email.isNullOrBlank()) {
    throw AppError.api(ErrorReason.Authentication, "Missing email claim")
  }

  return User(
    id = principal.subject ?: throw AppError.api(ErrorReason.Authentication, "Missing sub claim"),
    email = email,
    username = username,
    entitlements =
      principal.payload.getClaim(AuthConfiguration.entitlementsClaim).asList(String::class.java),
  )
}

fun Application.installJwtAuth(
  issuer: String,
  jwksEndpoint: String,
  leeway: Long,
  entitlementsClaim: String,
  audience: String,
) {
  AuthConfiguration.entitlementsClaim = entitlementsClaim

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

private object DummyJWTAuth {
  val key: String

  init {
    val keyGenerator = KeyGenerator.getInstance("HmacSHA256")
    keyGenerator.init(256) // Use 256-bit key for security
    val secretKey: SecretKey = keyGenerator.generateKey()
    key = Base64.getEncoder().encodeToString(secretKey.encoded)
  }
}

fun Application.noAuth() {
  AuthConfiguration.entitlementsClaim = "entitlements"

  install(Authentication) {
    jwt("admin") {
      verifier(JWT.require(Algorithm.HMAC256(DummyJWTAuth.key)).build())
      authHeader {
        val jwt =
          JWT.create()
            .withSubject("admin")
            .withClaim("email", "admin@ragu.com")
            .withClaim("entitlements", listOf("admin"))
            .withClaim("given_name", "admin")
            .withIssuedAt(Instant.MIN)
            .withExpiresAt(Instant.MAX)
            .sign(Algorithm.HMAC256(DummyJWTAuth.key))
        HttpAuthHeader.Single(AuthScheme.Bearer, jwt)
      }
      validate { JWTPrincipal(it.payload) }
    }
    jwt("user") {
      verifier(JWT.require(Algorithm.HMAC256(DummyJWTAuth.key)).build())
      authHeader {
        val jwt =
          JWT.create()
            .withSubject("user")
            .withClaim("email", "user@ragu.com")
            .withClaim("entitlements", listOf("user"))
            .withClaim("given_name", "user")
            .withIssuedAt(Instant.MIN)
            .withExpiresAt(Instant.MAX)
            .sign(Algorithm.HMAC256(DummyJWTAuth.key))
        HttpAuthHeader.Single(AuthScheme.Bearer, jwt)
      }
      validate { JWTPrincipal(it.payload) }
    }
  }
}
