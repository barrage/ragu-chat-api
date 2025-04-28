package net.barrage.llmao.core.token

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.types.KUUID

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.tokens.TokenUsageTracker")

/** Used for tracking token usage when embedding and performing inference. */
class TokenUsageTracker(
  private val user: User,
  // TODO: Change to workflow ID
  private val agentId: KUUID? = null,
  private val originType: String,
  private val originId: KUUID,
  private val repository: TokenUsageRepositoryWrite,
) {
  private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)

  fun store(amount: TokenUsageAmount, usageType: TokenUsageType, model: String, provider: String) {
    try {
      scope.launch {
        repository.insert(
          userId = user.id,
          username = user.username,
          agentId = agentId,
          origin = originType,
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
