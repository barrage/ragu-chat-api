package net.barrage.llmao.websocket

import io.ktor.server.plugins.*
import net.barrage.llmao.error.apiError
import net.barrage.llmao.error.internalError
import net.barrage.llmao.llm.Chat
import net.barrage.llmao.llm.factories.ChatFactory
import net.barrage.llmao.llm.types.ChatConfig
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.ChatService

class MessageHandler(private val chatFactory: ChatFactory) {
  private val chatService = ChatService()
  private val chats = mutableMapOf<KUUID, Chat>()

  suspend fun handleMessage(emitter: Emitter, message: C2SMessage) {
    try {
      when (message) {
        is C2SChatMessage -> this.handleChatMessage(emitter, message.userId, message.payload)
        is C2SServerMessageOpenChat -> this.openChat(emitter, message.userId, message.payload.body)
        is C2SServerMessageCloseChat -> this.closeChat(emitter, message.userId)
        is C2SServerMessageStopStream -> this.stopStream(emitter, message.userId)
      }
    } catch (e: Exception) {
      if (chats[message.userId] != null) {
        try {
          chatService.getUserChat(chats[message.userId]!!.config.id!!, message.userId)
        } catch (cse: NotFoundException) {
          emitter.emitError(apiError("Not Found", "Chat not found or deleted"))
          this.removeUserChat(message.userId)
          return
        }
      }

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
    val chat =
      chats[userId] ?: return emitter.emitError(apiError("Bad Request", "Have you opened a chat?"))

    if (chat.config.messageReceived == true) {
      chatService.getUserChat(chat.config.id!!, userId)
    }

    if (!chat.streamActive) {
      chat.stream(message)
    } else emitter.emitError(apiError("Bad Request", "Stream already active"))
  }

  private suspend fun closeChat(emitter: Emitter, userId: KUUID) {
    this.chats[userId]?.closeStream()
    this.chats.remove(userId)
    emitter.emitSystemMessage(S2CChatClosedMessage())
  }

  fun removeUserChat(userId: KUUID) {
    this.chats.remove(userId)
  }

  private suspend fun openChat(
    emitter: Emitter,
    userId: KUUID,
    message: C2SMessagePayloadOpenChatBody,
  ) {
    var chatId: KUUID? = message.chatId
    val llm = message.llm
    val language = message.language
    val agentId = message.agentId

    if (llm == null && chatId == null) {
      emitter.emitError(apiError("Bad Request", "`llm` parameter missing"))
    }

    if (agentId == null && chatId == null) {
      emitter.emitError(apiError("Bad Request", "`agentId` parameter missing"))
      return
    }

    val chat: Chat =
      if (chatId == null) {
        chatId = KUUID.randomUUID()
        val chatConfig =
          ChatConfig(id = chatId, userId = userId, agentId = agentId!!, language = language!!)

        chatFactory.new(llm!!, chatConfig, emitter)
      } else {
        chatFactory.fromExisting(chatId, userId, emitter)
      }

    this.chats[userId] = chat
    emitter.emitSystemMessage(S2CChatOpenMessage(ChatOpen(chatId!!, chat.config)))
  }

  private suspend fun stopStream(emitter: Emitter, userId: KUUID) {
    val chat = this.chats[userId]
    if (chat != null) {
      chat.closeStream()
      emitter.emitTerminator()
    }
  }
}
