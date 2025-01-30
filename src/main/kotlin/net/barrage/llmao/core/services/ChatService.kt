package net.barrage.llmao.core.services

import io.ktor.util.logging.*
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithAgent
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.EvaluateMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SearchFiltersAdminChats
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.UserRepository
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private val LOG = KtorSimpleLogger("net.barrage.llmao.core.services.ChatService")

/** Handles Chat CRUD. */
class ChatService(
  private val chatRepository: ChatRepository,
  private val agentRepository: AgentRepository,
  private val userRepository: UserRepository,
  private val avatarStorage: ImageStorage,
) {
  suspend fun listChatsAdmin(
    pagination: PaginationSort,
    filters: SearchFiltersAdminChats,
  ): CountedList<ChatWithUserAndAgent> {
    return chatRepository.getAllAdmin(pagination, filters)
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

    val agent = agentRepository.getAgent(chat.agentId)

    return ChatWithAgent(chat, agent)
  }

  suspend fun getChatWithUserAndAgent(id: KUUID): ChatWithUserAndAgent {
    val chat =
      chatRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent = agentRepository.getAgent(chat.agentId)
    val user = userRepository.get(chat.userId)!!

    return ChatWithUserAndAgent(chat, user, agent)
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

  suspend fun evaluateMessage(
    chatId: KUUID,
    messageId: KUUID,
    evaluation: EvaluateMessage,
  ): EvaluateMessage {
    val message =
      chatRepository.getMessage(chatId, messageId)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")

    if (message.senderType != "assistant") {
      throw AppError.api(ErrorReason.InvalidParameter, "Cannot evaluate non-assistant messages")
    }

    return chatRepository.evaluateMessage(messageId, evaluation)
      ?: throw AppError.api(ErrorReason.Internal, "Failed to evaluate message")
  }

  suspend fun evaluateMessage(
    chatId: KUUID,
    messageId: KUUID,
    userId: KUUID,
    input: EvaluateMessage,
  ): EvaluateMessage {
    val assistantMessage =
      chatRepository.getMessage(chatId, messageId)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")

    if (assistantMessage.senderType != "assistant" || assistantMessage.responseTo == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "Cannot evaluate non-assistant messages")
    }

    val userMessage =
      chatRepository.getMessage(chatId, assistantMessage.responseTo)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")

    if (userMessage.sender != userId) {
      throw AppError.api(ErrorReason.Authentication, "Cannot evaluate message")
    }

    return chatRepository.evaluateMessage(messageId, input)
      ?: throw AppError.api(ErrorReason.Internal, "Failed to evaluate message")
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
