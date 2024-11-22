package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.types.KUUID

/** Handles the registration and removal of WS tokens. */
class TokenManager {
  /** Maps one time tokens to user IDs. */
  private val tokens: MutableMap<KUUID, KUUID> = mutableMapOf()

  /** The reverse of `tokens`. Used to prevent overflowing the map. */
  private val pendingTokens: MutableMap<KUUID, KUUID> = mutableMapOf()

  /** Register a token and map it to the authenticating user's ID. */
  fun registerToken(userId: KUUID): KUUID {
    val existingToken = pendingTokens[userId]

    if (existingToken != null) {
      return existingToken
    }

    val token = KUUID.randomUUID()

    tokens[token] = userId
    pendingTokens[userId] = token

    return token
  }

  /**
   * Remove the token from the token map. If this returns a non-null value, the user is
   * authenticated.
   */
  fun removeToken(token: KUUID): KUUID? {
    val userId = tokens.remove(token) ?: return null
    pendingTokens.remove(userId)
    return userId
  }
}
