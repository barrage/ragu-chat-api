package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class Server(private val factory: ChatFactory) {
  /** Maps user IDs to their chat instances. */
  private val chats: MutableMap<KUUID, Chat> = mutableMapOf()

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

  fun removeChat(userId: KUUID) {
    chats.remove(userId)
  }

  suspend fun handleMessage(userId: KUUID, message: ClientMessage, emitter: Emitter) {
    when (message) {
      is ClientMessage.Chat -> handleChatMessage(emitter, userId, message.text)
      is ClientMessage.System -> handleSystemMessage(emitter, userId, message.payload)
    }
  }

  private fun handleChatMessage(emitter: Emitter, userId: KUUID, message: String) {
    val chat =
      chats[userId] ?: throw AppError.api(ErrorReason.Websocket, "Chat not open for user '$userId'")

    if (chat.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    chat.startStreaming(message, emitter)
  }

  private suspend fun handleSystemMessage(emitter: Emitter, userId: KUUID, message: SystemMessage) {
    when (message) {
      is SystemMessage.OpenNewChat -> {
        val chat = factory.new(userId, message.agentId)
        this.chats[userId] = chat
        emitter.emitServer(ServerMessage.ChatOpen(chat.id))
      }
      is SystemMessage.OpenExistingChat -> {
        val chat = chats[userId]
        if (chat != null) {
          emitter.emitServer(ServerMessage.ChatOpen(chat.id))
          return
        }
        val existingChat = factory.fromExisting(message.chatId)
        chats[userId] = existingChat
        emitter.emitServer(ServerMessage.ChatOpen(message.chatId))
        LOG.debug("Opened chat ({}) for user '{}'", existingChat.id, userId)
      }
      is SystemMessage.CloseChat -> {
        val chat = chats.remove(userId)
        chat?.let {
          emitter.emitServer(ServerMessage.ChatClosed(it.id))
          it.closeStream()
          LOG.debug("Closed chat for user '{}'", userId)
        }
      }
      is SystemMessage.StopStream -> {
        val chat = chats[userId]
        chat?.closeStream()
      }
    }
  }
}
