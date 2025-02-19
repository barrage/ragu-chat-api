package net.barrage.llmao.core.tokens

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.barrage.llmao.core.types.KUUID

internal val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.tokens.TokenUsageTracker")

/** Used for tracking token usage when embedding and performing inference. */
class TokenUsageTracker(
  private val userId: KUUID,
  private val agentId: KUUID,
  private val agentConfigurationId: KUUID,
  private val origin: String,
  private val originId: KUUID,
  private val repository: TokenUsageRepositoryWrite,
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {

  fun store(amount: Int, usageType: TokenUsageType, model: String, provider: String) {
    try {
      scope.launch {
        repository.insert(
          userId,
          agentId,
          agentConfigurationId,
          origin,
          originId,
          amount,
          usageType,
          model,
          provider,
        )
      }
    } catch (e: Exception) {
      LOG.error("Failed to store token usage", e)
    }
  }
}
