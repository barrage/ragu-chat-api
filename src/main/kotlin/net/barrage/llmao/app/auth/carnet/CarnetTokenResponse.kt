package net.barrage.llmao.app.auth.carnet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class CarnetTokenResponse(
  @SerialName("id_token") val idToken: String? = null,
  @SerialName("access_token") val accessToken: String,
  @SerialName("expires_in") val expiresIn: Int,
  @SerialName("token_type") val tokenType: String,
  @SerialName("refresh_token") val refreshToken: String? = null,
)
