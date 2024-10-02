package net.barrage.llmao.core.auth

import net.barrage.llmao.models.UserInfo

interface AuthenticationProvider {
  /** Return this provider's unique identifier. */
  fun id(): String

  /** Authenticate a user, returning their info upon successful authentication. */
  suspend fun authenticate(payload: LoginPayload): UserInfo
}
