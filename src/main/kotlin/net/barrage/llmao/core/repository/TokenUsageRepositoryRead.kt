package net.barrage.llmao.core.repository

import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.core.database.optionalEq
import net.barrage.llmao.core.token.TokenUsage
import net.barrage.llmao.core.token.TokenUsageAggregate
import net.barrage.llmao.core.token.toTokenUsage
import net.barrage.llmao.tables.references.TOKEN_USAGE
import net.barrage.llmao.types.KLocalDate
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KOffsetTime
import org.jooq.DSLContext
import java.time.LocalTime
import java.time.ZoneOffset

class TokenUsageRepositoryRead(private val dslContext: DSLContext) {
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
}
