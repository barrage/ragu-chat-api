package net.barrage.llmao.app.auth.carnet

import com.auth0.jwt.JWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
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

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.auth.carnet")

class CarnetAuthenticationProvider(
  private val client: HttpClient,
  private val tokenEndpoint: String,
  private val keysEndpoint: String,
  private val userInfoEndpoint: String,
  private val tokenIssuer: String,
  private val clientId: String,
  private val clientSecret: String,
) : AuthenticationProvider {
  override fun id(): String {
    return "carnet"
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
      throw AppError.api(ErrorReason.Authentication, "Failed to retrieve token: ${res.status}")
    }

    val response = res.body<CarnetTokenResponse>()
    val token = JWT.decode(response.accessToken)
    verifyJwtSignature(client, token, keysEndpoint, tokenIssuer, arrayOf(clientId))

    val tokenSub = token.subject

    if (token.expiresAtAsInstant.isBefore(Instant.now())) {
      throw AppError.api(ErrorReason.Authentication, "Token expired")
    }

    val carnetUserDataResponse =
      client.get(userInfoEndpoint) { this.bearerAuth(response.accessToken) }

    if (carnetUserDataResponse.status != HttpStatusCode.OK) {
      throw AppError.api(ErrorReason.Authentication, "Failed to retrieve user data")
    }

    val carnetUserData =
      try {
        carnetUserDataResponse.body<CarnetUserData>()
      } catch (_: Exception) {
        throw AppError.api(ErrorReason.Authentication, "Failed to parse user data")
      }

    val carnetId = carnetUserData.sub

    if (tokenSub != carnetId) {
      throw AppError.api(ErrorReason.Authentication, "Token and user data mismatch")
    }

    val email = carnetUserData.email
    if (email.isBlank()) {
      throw AppError.api(ErrorReason.Authentication, "Email not found")
    }

    return UserInfo(id = carnetId, email = email)
  }
}
