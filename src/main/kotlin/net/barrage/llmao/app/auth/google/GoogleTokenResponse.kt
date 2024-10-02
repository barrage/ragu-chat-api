package net.barrage.llmao.app.auth.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("expires_in") val expiresIn: Int,
  val scope: String,
  @SerialName("token_type") val tokenType: String,
  @SerialName("id_token") val idToken: String,
)
