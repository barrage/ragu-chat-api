package net.barrage.llmao.core.workflow

import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KUUID

/** Handles the registration and removal of WS tokens. */
class SessionTokenManager(private val maxTokensPerUser: Int = 100) {
  private val tokensPending: MutableMap<String, Int> = mutableMapOf()

  /** Maps one time tokens to user IDs. */
  private val tokens: MutableMap<String, User> = mutableMapOf()

  /** Register a token and map it to the authenticating user's ID. */
  fun registerToken(user: User): String {
    val tokenAmount = tokensPending.getOrDefault(user.id, 0)

    if (tokenAmount > maxTokensPerUser) {
      tokensPending.remove(user.id)
      tokens.entries.removeAll { it.value.id != user.id }
    }

    var token = KUUID.randomUUID().toString()

    tokensPending[user.id] = tokensPending.getOrDefault(user.id, 0) + 1
    tokens[token] = user

    return token
  }

  /**
   * Remove the token from the token map. If this returns a non-null value, the user is
   * authenticated.
   */
  fun removeToken(token: String): User? {
    val user = tokens.remove(token) ?: return null

    tokensPending[user.id] = tokensPending[user.id]?.let { it - 1 } ?: 0

    if (tokensPending[user.id] == 0) {
      tokensPending.remove(user.id)
    }

    return user
  }
}
