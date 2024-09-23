package net.barrage.llmao.services

import com.aallam.openai.api.core.FinishReason
import io.ktor.server.plugins.*
import net.barrage.llmao.dtos.chats.ChatDTO
import net.barrage.llmao.dtos.chats.ChatResponse
import net.barrage.llmao.dtos.chats.UpdateChatTitleDTO
import net.barrage.llmao.dtos.messages.EvaluateMessageDTO
import net.barrage.llmao.dtos.messages.FailedMessageDto
import net.barrage.llmao.dtos.messages.MessageDTO
import net.barrage.llmao.llm.types.ChatConfig
import net.barrage.llmao.llm.types.LLMConversationConfig
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.Message
import net.barrage.llmao.repositories.ChatRepository
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.LlmConfigsRecord

class ChatService {
  private val chatRepository = ChatRepository()

  fun getAll(
    page: Int,
    size: Int,
    sortBy: String,
    sortOrder: String,
    userId: KUUID? = null,
  ): ChatResponse {
    val offset = (page - 1) * size

    val chats = chatRepository.getAll(offset, size, sortBy, sortOrder, userId)
    val count = chatRepository.countAll(userId)

    return ChatResponse(chats, count)
  }

  fun get(id: KUUID): ChatDTO {
    try {
      return chatRepository.get(id)
    } catch (e: NoSuchElementException) {
      throw NotFoundException("Chat not found")
    }
  }

  fun getUserChat(id: KUUID, userId: KUUID): ChatDTO {
    try {
      return chatRepository.getUserChat(id, userId)
    } catch (e: NoSuchElementException) {
      throw NotFoundException("Chat not found")
    }
  }

  fun getMessages(id: KUUID, userId: KUUID? = null): List<Message> {
    return if (userId != null) {
      chatRepository.getMessagesForUser(id, userId)
    } else {
      chatRepository.getMessages(id)
    }
  }

  fun updateTitle(id: KUUID, updated: UpdateChatTitleDTO, userId: KUUID? = null): Chat {
    return if (userId != null) {
      chatRepository.updateTitleForUser(id, updated, userId)
        ?: throw NotFoundException("Chat not found")
    } else {
      chatRepository.updateTitle(id, updated) ?: throw NotFoundException("Chat not found")
    }
  }

  fun evaluateMessage(
    chatId: KUUID,
    messageId: KUUID,
    evaluation: EvaluateMessageDTO,
    userId: KUUID? = null,
  ): Message {
    if (userId != null) {
      chatRepository.getMessageForUser(chatId, messageId, userId)
        ?: throw NotFoundException("Message not found")
    } else {
      chatRepository.getMessage(chatId, messageId) ?: throw NotFoundException("Message not found")
    }

    return chatRepository.evaluateMessage(messageId, evaluation)
      ?: throw NotFoundException("Message not found")
  }

  fun insertWithConfig(chatConfig: ChatConfig, llmConfig: LLMConversationConfig): ChatDTO {
    val chat =
      ChatsRecord().apply {
        id = chatConfig.id
        userId = chatConfig.userId
        agentId = chatConfig.agentId
        title = chatConfig.title
      }

    val config =
      LlmConfigsRecord().apply {
        chatId = chatConfig.id
        model = llmConfig.model.name
        language = llmConfig.language.name
        temperature = llmConfig.chat.temperature
        streaming = llmConfig.chat.stream
      }

    return chatRepository.insertWithConfig(chat, config)
  }

  fun insertFailedMessage(
    finishReason: FinishReason,
    chatId: KUUID,
    userId: KUUID,
    returning: Boolean,
    content: String,
  ): FailedMessageDto? {
    return chatRepository.insertFailedMessage(finishReason, chatId, userId, returning, content)
  }

  fun insertUserMessage(id: KUUID, userId: KUUID, proompt: String): MessageDTO {
    return chatRepository.insertUserMessage(id, userId, proompt)
  }

  fun insertAssistantMessage(
    id: KUUID,
    agentId: Int,
    response: String,
    messageId: KUUID,
  ): MessageDTO {
    return chatRepository.insertAssistantMessage(id, agentId, response, messageId)
  }

  fun insertSystemMessage(id: KUUID, message: String): MessageDTO {
    return chatRepository.insertSystemMessage(id, message)
  }

  fun delete(id: KUUID) {
    if (chatRepository.delete(id) == 0) throw NotFoundException("Chat not found")
  }

  fun deleteChatUser(id: KUUID, userId: KUUID) {
    if (chatRepository.deleteChatUser(id, userId) == 0) throw NotFoundException("Chat not found")
  }
}
