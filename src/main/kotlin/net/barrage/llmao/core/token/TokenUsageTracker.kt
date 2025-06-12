package net.barrage.llmao.core.token

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.types.KUUID

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.tokens.TokenUsageTracker")

/** Used for tracking token usage when embedding and performing inference. */
class TokenUsageTracker(
  private val userId: String,
  private val username: String?,
  private val workflowId: KUUID,
  private val workflowType: String,
  private val repository: TokenUsageRepositoryWrite,
) {
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

  fun store(
    amount: TokenUsageAmount,
    usageType: TokenUsageType,
    model: String,
    provider: String,
    note: String? = null,
  ) {
    try {
      scope.launch {
        repository.insert(
          userId = userId,
          username = username,
          workflowId = workflowId,
          workflowType = workflowType,
          amountPrompt = amount.prompt,
          amountCompletion = amount.completion,
          amountTotal = amount.total,
          usageType = usageType,
          model = model,
          provider = provider,
          note = note,
        )
      }
    } catch (e: Exception) {
      LOG.error("Failed to store token usage", e)
    }
  }
}
