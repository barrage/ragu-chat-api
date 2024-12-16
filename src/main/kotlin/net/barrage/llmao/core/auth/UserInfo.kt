package net.barrage.llmao.core.auth

/** Ephemeral data when obtaining user information from authentication providers. */
data class UserInfo(
  /** The provider specific user ID. */
  val id: String,

  /** User email. */
  val email: String,

  /** Optional username. */
  val fullName: String? = null,

  /** Optional first name. */
  val firstName: String? = null,

  /** Optional last name. */
  val lastName: String? = null,

  /** Optional user profile image */
  val picture: String? = null,
)
