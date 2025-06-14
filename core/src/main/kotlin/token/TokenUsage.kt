package net.barrage.llmao.core.token

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.http.QueryParameter
import net.barrage.llmao.core.types.KLocalDate
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.TokenUsageRecord

/**
 * Aggregate of token usage displaying more clearly the amount of tokens used per provider and
 * model.
 */
@Serializable
data class TokenUsageAggregate(
  /** Displays the number of entries remaining for querying. */
  val total: Int,

  /** Maps the provider ID to the model ID to the total amount of tokens used. */
  val totalTokens: Map<String, Map<String, Int>>,

  /** Full usage details, per provider and model. */
  val usage: Map<String, Map<String, List<TokenUsage>>>,

  /** The usage start. */
  val startDate: KOffsetDateTime,

  /** Usage end. */
  val endDate: KOffsetDateTime,
)

/**
 * TABLE: token_usage
 *
 * Base model for token usage.
 */
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

/** DTO for querying token usage. */
data class TokenUsageListParameters(
  /** Filter by user ID. */
  @QueryParameter var userId: String? = null,

  /** Filter by workflow type. */
  @QueryParameter var workflowType: String? = null,

  /**
   * Display only entries after and including this date.
   *
   * If not provided, defaults to one month ago.
   */
  @QueryParameter var from: KLocalDate? = null,

  /**
   * Display only entries before and including this date.
   *
   * If not provided, defaults to today.
   */
  @QueryParameter var to: KLocalDate? = null,

  /** Per page. If provided, [offset] must also be provided. */
  @QueryParameter var limit: Int? = null,

  /** Page. If provided, [limit] must also be provided. */
  @QueryParameter var offset: Int? = null,
) {
  constructor() : this(null, null, null, null, null, null)
}

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
