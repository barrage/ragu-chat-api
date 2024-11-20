package net.barrage.llmao.app.auth.apple

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AppleTokenResponse(
  @SerialName("access_token") val accessToken: String,
  @SerialName("expires_in") val expiresIn: Int,
  @SerialName("id_token") val idToken: String,
  @SerialName("refresh_token") val refreshToken: String,
  @SerialName("token_type") val tokenType: String,
)
