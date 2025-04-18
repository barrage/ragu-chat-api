package net.barrage.llmao.core.api.admin

import java.time.ZoneOffset
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.ProvidersResponse
import net.barrage.llmao.core.model.AgentChatTimeSeries
import net.barrage.llmao.core.model.DashboardCounts
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Period
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.TokenUsageRepositoryRead
import net.barrage.llmao.core.token.TokenUsage
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

class AdminStatService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val tokenUsageRepository: TokenUsageRepositoryRead,
) {
  suspend fun listLanguageModels(provider: String): List<String> {
    val llmProvider = providers.llm.getProvider(provider)
    return llmProvider.listModels()
  }

  fun listProviders(): ProvidersResponse {
    return providers.list()
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

  suspend fun getTotalTokenUsageForPeriod(from: KOffsetDateTime?, to: KOffsetDateTime?): Number {
    if (from != null && to != null && from.isAfter(to)) {
      throw AppError.api(ErrorReason.InvalidParameter, "'from' must be before 'to'")
    }

    return tokenUsageRepository.getTotalTokenUsageForPeriod(
      from ?: KOffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
      to ?: KOffsetDateTime.now(),
    )
  }

  suspend fun listTokenUsage(
    userId: String? = null,
    agentId: KUUID? = null,
  ): CountedList<TokenUsage> {
    return tokenUsageRepository.listUsage(userId, agentId)
  }
}
