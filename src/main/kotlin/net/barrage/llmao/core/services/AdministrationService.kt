package net.barrage.llmao.core.services

import java.time.ZoneOffset
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.models.AgentChatTimeSeries
import net.barrage.llmao.core.models.DashboardCounts
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.Period
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.tokens.TokenUsage
import net.barrage.llmao.core.tokens.TokenUsageRepositoryRead
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AdministrationService(
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
