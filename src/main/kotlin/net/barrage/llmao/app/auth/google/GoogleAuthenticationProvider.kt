package net.barrage.llmao.app.auth.google

import com.auth0.jwt.JWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.logging.*
import java.time.Instant
import net.barrage.llmao.core.auth.AuthenticationProvider
import net.barrage.llmao.core.auth.LoginPayload
import net.barrage.llmao.core.auth.UserInfo
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.auth.google")

class GoogleAuthenticationProvider(
  val client: HttpClient,
  val tokenEndpoint: String,
  val keysEndpoint: String,
  val tokenIssuer: String,
  val clientId: String,
  val clientSecret: String,
) : AuthenticationProvider {
  override fun id(): String {
    return "google"
  }

  override suspend fun authenticate(payload: LoginPayload): UserInfo {
    val params =
      Parameters.build {
        append("code", payload.code)
        append("redirect_uri", payload.redirectUri)
        append("grant_type", payload.grantType)
        append("client_id", clientId)
        append("client_secret", clientSecret)
        append("code_verifier", payload.codeVerifier)
      }

    val res = client.submitForm(url = tokenEndpoint, params, encodeInQuery = false)

    if (res.status != HttpStatusCode.OK) {
      val errorBody = res.bodyAsText()
      LOG.error("Failed to retrieve token: ${res.status}. Response: $errorBody")
      throw AppError.api(
        ErrorReason.Authentication,
        "Failed to retrieve token: ${res.status}. Response: $errorBody",
      )
    }

    val response = res.body<GoogleTokenResponse>()
    val idToken = JWT.decode(response.idToken)

    verifyJwtSignature(client, idToken, keysEndpoint, tokenIssuer, arrayOf(clientId))

    val googleId = idToken.subject

    if (idToken.expiresAtAsInstant.isBefore(Instant.now())) {
      throw AppError.api(ErrorReason.Authentication, "Invalid token signature; Token expired")
    }

    val name = idToken.getClaim("name")?.asString()

    val givenName = idToken.getClaim("given_name")?.asString()

    val familyName = idToken.getClaim("family_name")?.asString()

    val isVerified = idToken.getClaim("email_verified")?.asBoolean() == true

    val picture = idToken.getClaim("picture")?.asString()

    val email =
      idToken.getClaim("email")?.asString()
        ?: throw AppError.api(ErrorReason.Authentication, "Email not found")

    if (!isVerified) {
      throw AppError.api(ErrorReason.Authentication, "Email not verified")
    }

    return UserInfo(googleId, email, name, givenName, familyName, picture)
  }
}
