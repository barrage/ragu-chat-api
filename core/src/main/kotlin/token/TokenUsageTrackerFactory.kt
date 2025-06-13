package net.barrage.llmao.core.token

import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.types.KUUID

/** Used for tracking token usage when embedding and performing inference. */
object TokenUsageTrackerFactory {
    private lateinit var repository: TokenUsageRepositoryWrite

    fun newTracker(
        userId: String,
        username: String,
        workflowType: String,
        workflowId: KUUID,
    ): TokenUsageTracker {
        return TokenUsageTracker(
            userId = userId,
            username = username,
            workflowType = workflowType,
            workflowId = workflowId,
            repository = repository,
        )
    }

    fun init(write: TokenUsageRepositoryWrite) {
        repository = write
    }
}
