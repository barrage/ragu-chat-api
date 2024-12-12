package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class MessageHandler(private val factory: ChatFactory) {
  /** Maps user IDs to their chat instances. */
  private val chats: MutableMap<Pair<KUUID, KUUID>, Chat> = mutableMapOf()

  fun removeChat(userId: KUUID, token: KUUID) {
    chats.remove(Pair(userId, token))
  }

  suspend fun handleMessage(userId: KUUID, token: KUUID, message: ClientMessage, channel: Channel) {
    when (message) {
      is ClientMessage.Chat -> handleChatMessage(userId, token, message.text)
      is ClientMessage.System -> handleSystemMessage(userId, token, message.payload, channel)
    }
  }

  private fun handleChatMessage(userId: KUUID, token: KUUID, message: String) {
    val chat =
      chats[Pair(userId, token)]
        ?: throw AppError.api(
          ErrorReason.Websocket,
          "Chat not open for user '$userId' with token '$token'",
        )

    if (chat.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    LOG.debug("Starting stream in '{}' for user '{}' with token '{}'", chat.id, userId, token)
    chat.startStreaming(message)
  }

  private suspend fun handleSystemMessage(
    userId: KUUID,
    token: KUUID,
    message: SystemMessage,
    channel: Channel,
  ) {
    when (message) {
      is SystemMessage.OpenNewChat -> {
        val chat = factory.new(userId, message.agentId, channel)
        chats[Pair(userId, token)] = chat
        channel.emitServer(ServerMessage.ChatOpen(chat.id))

        LOG.debug("Opened new chat ('{}') for '{}' with token '{}'", chat.id, userId, token)
      }
      is SystemMessage.OpenExistingChat -> {
        val chat = chats[Pair(userId, token)]

        // Prevent loading the same chat
        if (chat != null && chat.id == message.chatId) {
          LOG.debug("Existing chat has same ID as opened chat '{}'", chat.id)
          channel.emitServer(ServerMessage.ChatOpen(chat.id))
          return
        }

        chat?.cancelStream()

        val existingChat = factory.fromExisting(message.chatId, channel)

        chats[Pair(userId, token)] = existingChat

        channel.emitServer(ServerMessage.ChatOpen(message.chatId))

        LOG.debug(
          "Opened existing chat ('{}') for '{}' with token '{}'",
          existingChat.id,
          userId,
          token,
        )
      }
      is SystemMessage.CloseChat -> {
        chats.remove(Pair(userId, token))?.let {
          channel.emitServer(ServerMessage.ChatClosed(it.id))
          it.cancelStream()
          LOG.debug("Closed chat for user '{}' with token '{}'", userId, token)
        }
      }
      is SystemMessage.StopStream -> {
        LOG.debug("Stopping stream for user '{}' with token '{}'", userId, token)
        chats[Pair(userId, token)]?.cancelStream()
      }
    }
  }
}
