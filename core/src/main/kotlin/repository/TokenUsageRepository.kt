package net.barrage.llmao.core.repository

import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.database.optionalEq
import net.barrage.llmao.core.token.TokenUsage
import net.barrage.llmao.core.token.TokenUsageAggregate
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.core.token.toTokenUsage
import net.barrage.llmao.core.types.KLocalDate
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KOffsetTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.TOKEN_USAGE
import org.jooq.DSLContext

class TokenUsageRepository(private val dslContext: DSLContext) {
  suspend fun getUsageAggregateForPeriod(
    from: KLocalDate,
    to: KLocalDate,
    userId: String? = null,
    workflowType: String? = null,
    limit: Int? = 25,
    offset: Int? = 0,
  ): TokenUsageAggregate {
    val from = KOffsetDateTime(from, KOffsetTime.of(LocalTime.MIN, ZoneOffset.UTC))
    val to = KOffsetDateTime(to, KOffsetTime.of(LocalTime.MAX, ZoneOffset.UTC))

    val conditions =
      userId
        .optionalEq(TOKEN_USAGE.USER_ID)
        .and(workflowType.optionalEq(TOKEN_USAGE.WORKFLOW_TYPE))
        .and(TOKEN_USAGE.CREATED_AT.between(from, to))

    val total =
      dslContext.selectCount().from(TOKEN_USAGE).where(conditions).awaitFirstOrNull()?.value1() ?: 0

    // Maps provider -> model -> total tokens
    val totalTokensPerProvider = mutableMapOf<String, MutableMap<String, Int>>()

    // Maps provider -> model -> list of usages
    val usagePerProvider = mutableMapOf<String, MutableMap<String, MutableList<TokenUsage>>>()

    dslContext
      .selectFrom(TOKEN_USAGE)
      .where(conditions)
      .limit(limit)
      .offset(offset)
      .asFlow()
      .collect {
        val usage = it.toTokenUsage()

        val provider = usage.provider
        val model = usage.model

        totalTokensPerProvider
          .computeIfAbsent(provider) { _ -> mutableMapOf() }
          .computeIfAbsent(model) { _ -> 0 }
          .let { totalTokensPerProvider[provider]!![model] = it + usage.amount.total!! }

        usagePerProvider
          .computeIfAbsent(provider) { _ -> mutableMapOf() }
          .computeIfAbsent(model) { _ -> mutableListOf() }
          .add(usage)
      }

    return TokenUsageAggregate(
      total = total,
      totalTokens = totalTokensPerProvider,
      usage = usagePerProvider,
      startDate = from,
      endDate = to,
    )
  }

  suspend fun insert(
    userId: String?,
    username: String?,
    workflowId: KUUID?,
    workflowType: String?,
    amountPrompt: Int?,
    amountCompletion: Int?,
    amountTotal: Int?,
    usageType: TokenUsageType,
    model: String,
    provider: String,
    note: String? = null,
  ) {
    dslContext
      .insertInto(TOKEN_USAGE)
      .columns(
        TOKEN_USAGE.USER_ID,
        TOKEN_USAGE.USERNAME,
        TOKEN_USAGE.WORKFLOW_ID,
        TOKEN_USAGE.WORKFLOW_TYPE,
        TOKEN_USAGE.PROMPT_TOKENS_AMOUNT,
        TOKEN_USAGE.COMPLETION_TOKENS_AMOUNT,
        TOKEN_USAGE.TOTAL_TOKENS_AMOUNT,
        TOKEN_USAGE.USAGE_TYPE,
        TOKEN_USAGE.MODEL,
        TOKEN_USAGE.PROVIDER,
        TOKEN_USAGE.NOTE,
      )
      .values(
        userId,
        username,
        workflowId,
        workflowType,
        amountPrompt,
        amountCompletion,
        amountTotal,
        usageType.name,
        model,
        provider,
        note,
      )
      .awaitSingle()
  }
}
