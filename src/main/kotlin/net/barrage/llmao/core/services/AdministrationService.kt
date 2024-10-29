package net.barrage.llmao.core.services

import java.time.LocalDate
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.app.ProvidersResponse
import net.barrage.llmao.core.models.DashboardCounts
import net.barrage.llmao.core.models.GraphData
import net.barrage.llmao.core.models.LineChartKeys
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

  fun dashboardCounts(): DashboardCounts {
    return DashboardCounts(
      chatRepository.getChatCounts(),
      agentRepository.getAgentCounts(),
      userRepository.getUserCounts(),
    )
  }

  fun getChatHistoryCountsByAgent(period: Period): List<LineChartKeys> {
    val partial = chatRepository.agentsChatHistoryCounts(period)
    val dates = getDates(period)
    return fillPartial(partial, dates)
  }

  private fun getDates(period: Period): List<String> {
    return when (period) {
        Period.WEEK -> (0..6) // last 7 days
        Period.MONTH -> (0..29) // last 30 days
        Period.YEAR -> (0..11) // last 12 months
      }
      .map {
        if (period == Period.YEAR) {
          return@map LocalDate.now().withDayOfMonth(1).minusMonths(it.toLong()).toString()
        } else {
          return@map LocalDate.now().minusDays(it.toLong()).toString()
        }
      }
      .reversed()
  }

  private fun fillPartial(
    partial: Map<String, Map<String, Int>>,
    dates: List<String>,
  ): List<LineChartKeys> {
    val totals: MutableMap<String, Int> = dates.associateWith { 0 }.toMutableMap()

    val full: MutableList<LineChartKeys> =
      partial
        .map { LineChartKeys(it.key, dates.map { date -> GraphData(date, it.value[date] ?: 0) }) }
        .toMutableList()

    partial.forEach { (_, history) ->
      history.forEach { (date, count) -> if (date in dates) totals[date] = totals[date]!! + count }
    }

    full.add(LineChartKeys("Total", totals.map { GraphData(it.key, it.value) }))

    return full
  }
}
