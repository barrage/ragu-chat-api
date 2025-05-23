package net.barrage.llmao.core.administration

import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.PluginConfiguration
import net.barrage.llmao.core.Plugins
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.ProvidersResponse
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.repository.TokenUsageRepositoryRead
import net.barrage.llmao.core.token.TokenUsage
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID
import java.time.ZoneOffset

object Administration {
  private lateinit var providers: ProviderState
  private lateinit var tokenUsageRead: TokenUsageRepositoryRead
  private lateinit var plugins: Plugins
  private lateinit var settings: Settings

  fun init(
    providers: ProviderState,
    tokenUsageRead: TokenUsageRepositoryRead,
    plugins: Plugins,
    settings: Settings,
  ) {
    this.providers = providers
    this.tokenUsageRead = tokenUsageRead
    this.plugins = plugins
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
    return plugins.list(settings.getAll())
  }

  suspend fun getTotalTokenUsageForPeriod(from: KOffsetDateTime?, to: KOffsetDateTime?): Number {
    if (from != null && to != null && from.isAfter(to)) {
      throw AppError.api(ErrorReason.InvalidParameter, "'from' must be before 'to'")
    }

    return tokenUsageRead.getTotalTokenUsageForPeriod(
      from ?: KOffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
      to ?: KOffsetDateTime.now(),
    )
  }

  suspend fun listTokenUsage(
    userId: String? = null,
    agentId: KUUID? = null,
  ): CountedList<TokenUsage> {
    return tokenUsageRead.listUsage(userId, agentId)
  }
}
