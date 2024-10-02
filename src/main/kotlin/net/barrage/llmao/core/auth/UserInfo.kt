package net.barrage.llmao.core.auth

/** Ephemeral data when obtaining user information from authentication providers. */
data class UserInfo(
  /** The provider specific user ID. */
  val id: String,

  /** User email. */
  val email: String,

  /** Mandatory username. */
  val fullName: String,

  /** Optional first name. */
  val firstName: String?,

  /** Optional last name. */
  val lastName: String?,
)
