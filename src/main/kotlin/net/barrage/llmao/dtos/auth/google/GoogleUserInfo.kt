package net.barrage.llmao.dtos.auth.google

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleUserInfo(
  val name: String,
  val email: String,
  @SerialName("given_name") val givenName: String,
  @SerialName("family_name") val familyName: String,
  val picture: String,
  val hd: String,
)
