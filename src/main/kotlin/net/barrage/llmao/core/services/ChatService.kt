package net.barrage.llmao.core.services

import io.ktor.util.logging.*
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private val LOG = KtorSimpleLogger("net.barrage.llmao.core.services.ChatService")

/** Handles Chat CRUD. */
class ChatService(
  private val chatRepository: ChatRepository,
  private val agentRepository: AgentRepository,
  private val userRepository: UserRepository,
) {
  fun listChatsAdmin(
    pagination: PaginationSort,
    userId: KUUID?,
  ): CountedList<ChatWithUserAndAgent> {
    return chatRepository.getAllAdmin(pagination, userId)
  }

  fun listChats(pagination: PaginationSort, userId: KUUID): CountedList<Chat> {
    return chatRepository.getAll(pagination, userId)
  }

  fun getChatWithAgent(id: KUUID, userId: KUUID): ChatWithAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    if (userId != chat.userId) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    val agent = agentRepository.get(chat.agentId)

    return ChatWithAgent(chat, agent.agent)
  }

  fun getChatWithUserAndAgent(id: KUUID): ChatWithUserAndAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent = agentRepository.get(chat.agentId)

    val user = userRepository.get(chat.userId)!!

    return ChatWithUserAndAgent(chat, user, agent.agent)
  }

  fun getChat(chatId: KUUID): ChatWithMessages {
    return chatRepository.getWithMessages(chatId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat with ID '$chatId'")
  }

  fun updateTitle(chatId: KUUID, title: String): Chat {
    return chatRepository.updateTitle(chatId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  fun updateTitle(chatId: KUUID, userId: KUUID, title: String): Chat {
    return chatRepository.updateTitle(chatId, userId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  fun deleteChat(id: KUUID) {
    chatRepository.delete(id)
  }

  fun deleteChat(id: KUUID, userId: KUUID) {
    chatRepository.delete(id, userId)
  }

  fun evaluateMessage(chatId: KUUID, messageId: KUUID, evaluation: Boolean): Message {
    chatRepository.getMessage(chatId, messageId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
    return chatRepository.evaluateMessage(messageId, evaluation)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
  }

  fun evaluateMessage(
    chatId: KUUID,
    messageId: KUUID,
    userId: KUUID,
    evaluation: Boolean,
  ): Message {
    chatRepository.getMessage(chatId, messageId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
    return chatRepository.evaluateMessage(messageId, userId, evaluation)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
  }

  fun getMessages(id: KUUID, userId: KUUID? = null): List<Message> {
    val messages =
      if (userId != null) {
        chatRepository.getMessagesForUser(id, userId)
      } else {
        chatRepository.getMessages(id)
      }

    if (messages.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    return messages
  }
}
