package net.barrage.llmao.app.auth

import com.auth0.jwt.JWT
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.barrage.llmao.core.AuthenticationProvider
import net.barrage.llmao.dtos.auth.LoginPayload
import net.barrage.llmao.dtos.auth.google.GoogleTokenResponse
import net.barrage.llmao.dtos.auth.google.GoogleUserInfo
import net.barrage.llmao.error.apiError
import net.barrage.llmao.models.UserInfo

class GoogleAuthenticationProvider(
  private val client: HttpClient,
  private val tokenEndpoint: String,
  private val userEndpoint: String,
  private val clientId: String,
  private val clientSecret: String,
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
      }

    val res = client.submitForm(url = tokenEndpoint, params, encodeInQuery = false)
    val response = res.body<GoogleTokenResponse>()

    val idToken = JWT.decode(response.idToken)

    val googleId = idToken.subject
    val isVerified = idToken.getClaim("email_verified")?.asBoolean()

    if (isVerified == null || !isVerified) {
      throw apiError("Authentication", "Email not verified")
    }

    val userInfo =
      client
        .get(userEndpoint) { header("Authorization", "Bearer ${response.accessToken}") }
        .body<GoogleUserInfo>()

    return UserInfo(
      googleId,
      userInfo.email,
      userInfo.name,
      userInfo.givenName,
      userInfo.familyName,
    )
  }
}
