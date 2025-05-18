package net.barrage.llmao.core.token

import kotlinx.serialization.Serializable
import net.barrage.llmao.tables.records.TokenUsageRecord
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

@Serializable
data class TokenUsage(
  val id: Int,
  val userId: String,
  val username: String,
  val workflowId: KUUID?,
  val workflowType: String?,
  val amount: TokenUsageAmount,
  val usageType: TokenUsageType,
  val model: String,
  val provider: String,
  val note: String?,
  val createdAt: KOffsetDateTime,
)

fun TokenUsageRecord.toTokenUsage() =
  TokenUsage(
    id = id!!,
    userId = userId,
    username = username,
    workflowId = workflowId,
    workflowType = workflowType,
    amount = TokenUsageAmount(promptTokensAmount, completionTokensAmount, totalTokensAmount),
    usageType = TokenUsageType.valueOf(usageType),
    model = model,
    note = note,
    provider = provider,
    createdAt = createdAt!!,
  )

@Serializable data class TokenUsageAmount(val prompt: Int?, val completion: Int?, val total: Int?)

@Serializable
enum class TokenUsageType {
  /** Tokens were used for embedding the input query. */
  EMBEDDING,

  /** Tokens were used in chat completion (streams). */
  COMPLETION,

  /** Tokens were used in title generation. */
  COMPLETION_TITLE,
}
