package net.barrage.llmao.core.services

import io.ktor.util.logging.*
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.Pagination
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
  suspend fun listChatsAdmin(
    pagination: PaginationSort,
    userId: KUUID?,
  ): CountedList<ChatWithUserAndAgent> {
    return chatRepository.getAllAdmin(pagination, userId)
  }

  suspend fun listChats(pagination: PaginationSort, userId: KUUID): CountedList<Chat> {
    return chatRepository.getAll(pagination, userId)
  }

  suspend fun getChatWithAgent(id: KUUID, userId: KUUID): ChatWithAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    if (userId != chat.userId) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    val agent = agentRepository.get(chat.agentId)

    return ChatWithAgent(chat, agent.agent)
  }

  suspend fun getChatWithUserAndAgent(id: KUUID): ChatWithUserAndAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent = agentRepository.get(chat.agentId)

    val user = userRepository.get(chat.userId)!!

    return ChatWithUserAndAgent(chat, user, agent.agent)
  }

  suspend fun getChat(chatId: KUUID, pagination: Pagination): ChatWithMessages {
    return chatRepository.getWithMessages(chatId, pagination)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat with ID '$chatId'")
  }

  suspend fun updateTitle(chatId: KUUID, title: String): Chat {
    return chatRepository.updateTitle(chatId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  suspend fun updateTitle(chatId: KUUID, userId: KUUID, title: String): Chat {
    return chatRepository.updateTitle(chatId, userId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  suspend fun deleteChat(id: KUUID) {
    chatRepository.delete(id)
  }

  suspend fun deleteChat(id: KUUID, userId: KUUID) {
    chatRepository.delete(id, userId)
  }

  suspend fun evaluateMessage(chatId: KUUID, messageId: KUUID, evaluation: Boolean): Message {
    chatRepository.getMessage(chatId, messageId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
    return chatRepository.evaluateMessage(messageId, evaluation)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
  }

  suspend fun evaluateMessage(
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

  suspend fun getMessages(id: KUUID, userId: KUUID? = null, pagination: Pagination): List<Message> {
    val messages =
      if (userId != null) {
        chatRepository.getMessagesForUser(id, userId, pagination)
      } else {
        chatRepository.getMessages(id, pagination)
      }

    if (messages.total == 0) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    return messages.items
  }
}
