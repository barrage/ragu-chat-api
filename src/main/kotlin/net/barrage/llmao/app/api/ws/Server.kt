package net.barrage.llmao.app.api.ws

import io.ktor.server.plugins.*
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class Server(private val factory: ChatFactory) {
  private val chats: MutableMap<KUUID, Chat> = mutableMapOf()

  fun removeChat(userId: KUUID) {
    chats.remove(userId)
  }

  suspend fun handleMessage(userId: KUUID, message: ClientMessage, emitter: Emitter) {
    when (message) {
      is ClientMessage.Chat -> handleChatMessage(emitter, userId, message.text)
      is ClientMessage.System -> handleSystemMessage(emitter, userId, message.payload)
    }
  }

  private suspend fun handleChatMessage(emitter: Emitter, userId: KUUID, message: String) {
    val chat = chats[userId]

    if (chat == null) {
      emitter.emitError(
        AppError.api(ErrorReason.Websocket, "Unable to process message, have you opened a chat?")
      )
      return
    }

    if (chat.isStreaming()) {
      emitter.emitError(AppError.api(ErrorReason.Websocket, "Stream already active"))
    }

    chat.stream(message, emitter)
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
      }
      is SystemMessage.CloseChat -> {
        val chat = chats.remove(userId)
        chat?.let {
          emitter.emitServer(ServerMessage.ChatClosed(it.id))
          it.closeStream()
        }
      }
      is SystemMessage.StopStream -> {
        val chat = chats[userId]
        // Closing the stream during streaming will result emit the event
        // from the chat
        chat?.closeStream()
      }
    }
  }
}
