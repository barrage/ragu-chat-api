package net.barrage.llmao.websocket

import io.ktor.server.plugins.*
import net.barrage.llmao.error.apiError
import net.barrage.llmao.error.internalError
import net.barrage.llmao.llm.Chat
import net.barrage.llmao.llm.factories.ChatFactory
import net.barrage.llmao.serializers.KUUID

class Server(private val factory: ChatFactory) {
  private val chats: MutableMap<KUUID, Chat> = mutableMapOf()

  fun removeChat(userId: KUUID) {
    chats.remove(userId)
  }

  suspend fun handleMessage(userId: KUUID, message: ClientMessage, emitter: Emitter) {
    println(userId)
    try {
      when (message) {
        is ClientMessage.Chat -> handleChatMessage(emitter, userId, message.text)
        is ClientMessage.System -> handleSystemMessage(emitter, userId, message.payload)
      }
    } catch (e: Exception) {
      when (e) {
        is NoSuchElementException -> emitter.emitError(apiError("Not Found", e.message))
        is NotFoundException -> emitter.emitError(apiError("Not Found", e.message))
        is IllegalArgumentException -> emitter.emitError(apiError("Bad Request", e.message))
        is BadRequestException -> emitter.emitError(apiError("Bad Request", e.message))
        else -> {
          e.printStackTrace()
          emitter.emitError(internalError())
        }
      }
    }
  }

  private suspend fun handleChatMessage(emitter: Emitter, userId: KUUID, message: String) {
    val chat = chats[userId]

    if (chat == null) {
      emitter.emitError(apiError("Bad Request", "Have you opened a chat?"))
      return
    }

    if (chat.isStreaming()) {
      emitter.emitError(apiError("Bad Request", "Stream already active"))
    }

    chat.stream(message, emitter)
  }

  private suspend fun handleSystemMessage(emitter: Emitter, userId: KUUID, message: SystemMessage) {
    when (message) {
      is SystemMessage.OpenNewChat -> {
        val (
          llmProvider,
          agentId,
          llm,
          language,
        ) = message

        val chat = factory.new(llmProvider, userId, agentId, llm, language)
        this.chats[userId] = chat
        emitter.emitServerMessage(ServerMessage.ChatOpen(chat.id))
      }
      is SystemMessage.OpenExistingChat -> {
        val chat = chats[userId]
        if (chat != null) {
          emitter.emitServerMessage(ServerMessage.ChatOpen(chat.id))
          return
        }
        val existingChat = factory.fromExisting(message.chatId)
        chats[userId] = existingChat
        emitter.emitServerMessage(ServerMessage.ChatOpen(message.chatId))
      }
      is SystemMessage.CloseChat -> {
        val chat = chats.remove(userId)
        chat?.let {
          emitter.emitServerMessage(ServerMessage.ChatClosed(it.id))
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
