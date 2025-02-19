package net.barrage.llmao.core.tokens

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.TokenUsageRecord

@Serializable
data class TokenUsage(
  val id: Int,
  val userId: KUUID?,
  val agentId: KUUID?,
  val agentConfigurationId: KUUID?,
  val origin: TokenUsageOrigin,
  val amount: Int,
  val usageType: TokenUsageType,
  val model: String,
  val provider: String,
  val createdAt: KOffsetDateTime,
)

fun TokenUsageRecord.toTokenUsage() =
  TokenUsage(
    id = this.id!!,
    userId = this.userId,
    agentId = this.agentId,
    agentConfigurationId = this.agentConfigurationId,
    origin = TokenUsageOrigin(this.origin, this.originId),
    amount = this.amount,
    usageType = TokenUsageType.valueOf(this.usageType),
    model = this.model,
    provider = this.provider,
    createdAt = this.createdAt!!,
  )

@Serializable data class TokenUsageOrigin(val type: String, val id: KUUID?)

@Serializable
enum class TokenUsageType {
  /** Tokens were used for embedding the input query. */
  EMBEDDING,

  /** Tokens were used in chat completion (streams). */
  COMPLETION,

  /** Tokens were used in title generation. */
  COMPLETION_TITLE,

  /** Tokens were used in summary generation. */
  COMPLETION_SUMMARY,
}
