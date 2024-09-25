package net.barrage.llmao.models

/** Ephemeral data when obtaining user information from authentication providers. */
data class UserInfo(
  /** The provider specific user ID. */
  val id: String,
  val email: String,
  val fullName: String,
  val firstName: String?,
  val lastName: String?,
)
