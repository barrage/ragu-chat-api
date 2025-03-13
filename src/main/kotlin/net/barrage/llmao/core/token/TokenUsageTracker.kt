package net.barrage.llmao.core.token

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.barrage.llmao.core.types.KUUID

internal val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.tokens.TokenUsageTracker")

/** Used for tracking token usage when embedding and performing inference. */
class TokenUsageTracker(
  private val userId: String?,
  private val username: String?,
  private val agentId: KUUID?,
  private val agentConfigurationId: KUUID?,
  private val origin: String,
  private val originId: KUUID?,
  private val repository: TokenUsageRepositoryWrite,
) {
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

  fun store(amount: TokenUsageAmount, usageType: TokenUsageType, model: String, provider: String) {
    try {
      scope.launch {
        repository.insert(
          userId = userId,
          username = username,
          agentId = agentId,
          agentConfigurationId = agentConfigurationId,
          origin = origin,
          originId = originId,
          amountPrompt = amount.prompt,
          amountCompletion = amount.completion,
          amountTotal = amount.total,
          usageType = usageType,
          model = model,
          provider = provider,
        )
      }
    } catch (e: Exception) {
      LOG.error("Failed to store token usage", e)
    }
  }
}
