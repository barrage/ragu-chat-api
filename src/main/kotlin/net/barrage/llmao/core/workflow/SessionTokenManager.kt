package net.barrage.llmao.core.workflow

import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID

private val LOG =
  KtorSimpleLogger("net.barrage.llmao.app.api.ws.WebsocketTokenManager")

/** Handles the registration and removal of WS tokens. */
class SessionTokenManager {
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

    LOG.debug("{} - token registered '{}'", user.id, token)

    return token
  }

  /**
   * Remove the token from the token map. If this returns a non-null value, the user is
   * authenticated.
   */
  fun removeToken(token: KUUID): User? {
    LOG.debug("removing token '{}'", token)
    val userId = tokens.remove(token) ?: return null
    pendingTokens.remove(userId)
    return userId
  }
}
