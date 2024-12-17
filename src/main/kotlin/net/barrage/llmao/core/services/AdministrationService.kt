package net.barrage.llmao.core.services

import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.models.AgentChatTimeSeries
import net.barrage.llmao.core.models.DashboardCounts
import net.barrage.llmao.core.models.common.Period
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.repository.UserRepository

class AdministrationService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepository: ChatRepository,
  private val userRepository: UserRepository,
) {
  suspend fun listLanguageModels(provider: String): List<String> {
    val llmProvider = providers.llm.getProvider(provider)
    return llmProvider.listModels()
  }

  fun listProviders(): ProvidersResponse {
    return providers.list()
  }

  suspend fun dashboardCounts(): DashboardCounts {
    return DashboardCounts(
      chatRepository.getChatCounts(),
      agentRepository.getAgentCounts(),
      userRepository.getUserCounts(),
    )
  }

  fun getChatHistoryCountsByAgent(period: Period): AgentChatTimeSeries {
    val agentChatsPerDate = chatRepository.agentsChatHistoryCounts(period)

    val timeSeries = AgentChatTimeSeries.builder<Long, String>(period, 0)

    for (data in agentChatsPerDate) {
      timeSeries.addDataPoint(data.agentId.toString(), data.date?.toString(), data.amount)
      timeSeries.addLegend(data.agentId.toString(), data.agentName)
    }

    return timeSeries.build()
  }
}
