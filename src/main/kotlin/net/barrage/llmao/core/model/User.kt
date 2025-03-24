package net.barrage.llmao.core.model

/** User DTO obtained from the access token. */
data class User(
  val id: String,
  val username: String?,
  val email: String?,
  val entitlements: List<String>,
)
