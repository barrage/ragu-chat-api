package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID

/** Handles the registration and removal of WS tokens. */
class WebsocketTokenManager {
  /** Maps one time tokens to user IDs. */
  private val tokens: MutableMap<KUUID, User> = mutableMapOf()

  /** The reverse of `tokens`. Used to prevent overflowing the map. */
  private val pendingTokens: MutableMap<User, KUUID> = mutableMapOf()

  /** Register a token and map it to the authenticating user's ID. */
  fun registerToken(user: User): KUUID {
    val existingToken = pendingTokens[user]

    if (existingToken != null) {
      return existingToken
    }

    val token = KUUID.randomUUID()

    tokens[token] = user
    pendingTokens[user] = token

    return token
  }

  /**
   * Remove the token from the token map. If this returns a non-null value, the user is
   * authenticated.
   */
  fun removeToken(token: KUUID): User? {
    val userId = tokens.remove(token) ?: return null
    pendingTokens.remove(userId)
    return userId
  }
}
