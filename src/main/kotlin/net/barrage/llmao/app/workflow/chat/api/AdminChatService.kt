package net.barrage.llmao.app.workflow.chat.api

import net.barrage.llmao.app.workflow.chat.model.AgentChatTimeSeries
import net.barrage.llmao.app.workflow.chat.model.Chat
import net.barrage.llmao.app.workflow.chat.model.ChatWithAgent
import net.barrage.llmao.app.workflow.chat.model.DashboardCounts
import net.barrage.llmao.app.workflow.chat.model.SearchFiltersAdminChats
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.model.common.Period
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

  suspend fun dashboardCounts(): DashboardCounts {
    return DashboardCounts(chatRepositoryRead.getChatCounts(), agentRepository.getAgentCounts())
  }

  suspend fun getChatHistoryCountsByAgent(period: Period): AgentChatTimeSeries {
    val agentChatsPerDate = chatRepositoryRead.agentsChatHistoryCounts(period)

    val timeSeries = AgentChatTimeSeries.builder<Long, String>(period, 0)

    for (data in agentChatsPerDate) {
      timeSeries.addDataPoint(data.agentId.toString(), data.date?.toString(), data.amount)
      timeSeries.addLegend(data.agentId.toString(), data.agentName)
    }

    return timeSeries.build()
  }
}
