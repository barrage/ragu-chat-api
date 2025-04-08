package net.barrage.llmao.core.api.admin

import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.ChatWithAgent
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.model.common.SearchFiltersAdminChats
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.types.KUUID

/** Handles Chat CRUD. */
class AdminChatService(
  private val chatRepositoryRead: ChatRepositoryRead,
  private val agentRepository: AgentRepository,
) {
  suspend fun listChatsAdmin(
    pagination: PaginationSort,
    filters: SearchFiltersAdminChats,
  ): CountedList<ChatWithAgent> {
    return chatRepositoryRead.getAllAdmin(pagination, filters)
  }

  suspend fun getChatWithAgent(id: KUUID): ChatWithAgent {
    val chat =
      chatRepositoryRead.get(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent =
      agentRepository.getAgent(chat.agentId)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

    return ChatWithAgent(chat, agent)
  }

  suspend fun updateTitle(chatId: KUUID, title: String): Chat {
    return chatRepositoryRead.updateTitle(chatId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  suspend fun deleteChat(id: KUUID) {
    chatRepositoryRead.delete(id)
  }

  suspend fun evaluateMessage(chatId: KUUID, messageGroupId: KUUID, input: EvaluateMessage) {
    chatRepositoryRead.get(chatId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")

    val amount = chatRepositoryRead.evaluateMessageGroup(messageGroupId, input)
    if (amount == 0) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
    }
  }

  suspend fun getMessages(id: KUUID, pagination: Pagination): CountedList<MessageGroupAggregate> {
    val messages =
      chatRepositoryRead.getMessages(chatId = id, userId = null, pagination = pagination)

    if (messages.total == 0) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    return messages
  }
}
