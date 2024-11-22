package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class MessageHandler(private val factory: ChatFactory) {
  /** Maps user IDs to their chat instances. */
  private val chats: MutableMap<KUUID, Chat> = mutableMapOf()

  fun removeChat(userId: KUUID) {
    chats.remove(userId)
  }

  suspend fun handleMessage(userId: KUUID, message: ClientMessage, channel: Channel) {
    when (message) {
      is ClientMessage.Chat -> handleChatMessage(userId, message.text)
      is ClientMessage.System -> handleSystemMessage(userId, message.payload, channel)
    }
  }

  private fun handleChatMessage(userId: KUUID, message: String) {
    val chat =
      chats[userId] ?: throw AppError.api(ErrorReason.Websocket, "Chat not open for user '$userId'")

    if (chat.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    chat.startStreaming(message)
  }

  private suspend fun handleSystemMessage(userId: KUUID, message: SystemMessage, channel: Channel) {
    when (message) {
      is SystemMessage.OpenNewChat -> {
        val chat = factory.new(userId, message.agentId, channel)
        chats[userId] = chat
        channel.emitServer(ServerMessage.ChatOpen(chat.id))

        LOG.debug("Opened new chat ('{}') for '{}'", chat.id, userId)
      }
      is SystemMessage.OpenExistingChat -> {
        val chat = chats[userId]

        if (chat != null && chat.id == message.chatId) {
          channel.emitServer(ServerMessage.ChatOpen(chat.id))
          return
        }

        val existingChat = factory.fromExisting(message.chatId, channel)

        chats[userId] = existingChat

        channel.emitServer(ServerMessage.ChatOpen(message.chatId))

        LOG.debug("Opened existing chat ('{}') for '{}'", existingChat.id, userId)
      }
      is SystemMessage.CloseChat -> {
        chats.remove(userId)?.let {
          channel.emitServer(ServerMessage.ChatClosed(it.id))
          it.closeStream()
          LOG.debug("Closed chat for user '{}'", userId)
        }
      }
      is SystemMessage.StopStream -> {
        chats[userId]?.closeStream()
      }
    }
  }
}
