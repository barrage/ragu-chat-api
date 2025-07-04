package net.barrage.llmao.core

import net.barrage.llmao.core.model.common.Period
import net.barrage.llmao.core.repository.TokenUsageRepository
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.token.TokenUsageAggregate
import net.barrage.llmao.core.token.TokenUsageListParameters
import net.barrage.llmao.core.types.KLocalDate

object Administration {
  private lateinit var providers: ProviderState
  private lateinit var tokenUsage: TokenUsageRepository
  private lateinit var settings: Settings

  fun init(providers: ProviderState, tokenUsage: TokenUsageRepository, settings: Settings) {
    this.providers = providers
    this.tokenUsage = tokenUsage
    this.settings = settings
  }

  /** List all available service providers. */
  fun listProviders(): ProvidersResponse {
    return providers.list()
  }

  /** List the supported LLMs for a given provider. */
  suspend fun listLanguageModels(provider: String): List<String> {
    val llmProvider = providers.llm[provider]
    return llmProvider.listModels()
  }

  suspend fun listPlugins(): List<PluginConfiguration> {
    return Plugins.list(settings.getAll())
  }

  suspend fun aggregateTokenUsage(params: TokenUsageListParameters): TokenUsageAggregate {
    if (params.from != null && params.to != null && params.from!!.isAfter(params.to)) {
      throw AppError.api(ErrorReason.InvalidParameter, "'dateFrom' must be before 'dateTo'")
    }

    if (params.to != null && params.to!!.isAfter(KLocalDate.now())) {
      throw AppError.api(ErrorReason.InvalidParameter, "'dateTo' must be today or in the past")
    }

    if (params.offset != null && params.limit == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "'limit' must be provided if using 'offset'")
    }

    if (params.limit != null && params.offset == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "'offset' must be provided if using 'limit'")
    }

    if (params.limit != null && params.limit!! < 0) {
      throw AppError.api(ErrorReason.InvalidParameter, "'limit' must be positive")
    }

    if (params.offset != null && params.offset!! < 0) {
      throw AppError.api(ErrorReason.InvalidParameter, "'offset' must be positive")
    }

    val from = params.from ?: Period.MONTH.toDateBeforeNow()
    val to = params.to ?: KLocalDate.now()

    return tokenUsage.getUsageAggregateForPeriod(
      from = from,
      to = to,
      userId = params.userId,
      workflowType = params.workflowType,
      limit = params.limit,
      offset = params.offset,
    )
  }
}
