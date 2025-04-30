package net.barrage.llmao.core.token

import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.types.KUUID

/** Used for tracking token usage when embedding and performing inference. */
object TokenUsageTrackerFactory {
  private lateinit var repository: TokenUsageRepositoryWrite

  fun newTracker(user: User, workflowType: String, workflowId: KUUID): TokenUsageTracker {
    return TokenUsageTracker(
      user = user,
      workflowType = workflowType,
      workflowId = workflowId,
      repository = repository,
    )
  }

  fun init(write: TokenUsageRepositoryWrite) {
    repository = write
  }
}
