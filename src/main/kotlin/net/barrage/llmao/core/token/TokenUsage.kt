package net.barrage.llmao.core.token

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.TokenUsageRecord

@Serializable
data class TokenUsage(
  val id: Int,
  val userId: String?,
  val agentId: KUUID?,
  val agentConfigurationId: KUUID?,
  val origin: TokenUsageOrigin,
  val amount: TokenUsageAmount,
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
    amount =
      TokenUsageAmount(
        this.promptTokensAmount,
        this.completionTokensAmount,
        this.totalTokensAmount,
      ),
    usageType = TokenUsageType.valueOf(this.usageType),
    model = this.model,
    provider = this.provider,
    createdAt = this.createdAt!!,
  )

@Serializable data class TokenUsageAmount(val prompt: Int?, val completion: Int?, val total: Int?)

/** Origin of token usage. */
@Serializable
data class TokenUsageOrigin(
  /** The origin type, i.e. the type of workflow that caused the usage. */
  val type: String,

  /** The workflow ID. */
  val id: KUUID?,
)

@Serializable
enum class TokenUsageType {
  /** Tokens were used for embedding the input query. */
  EMBEDDING,

  /** Tokens were used in chat completion (streams). */
  COMPLETION,

  /** Tokens were used in title generation. */
  COMPLETION_TITLE,
}
