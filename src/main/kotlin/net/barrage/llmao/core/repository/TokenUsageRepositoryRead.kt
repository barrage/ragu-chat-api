package net.barrage.llmao.core.repository

import java.time.temporal.ChronoUnit
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.database.optionalEq
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Period
import net.barrage.llmao.core.token.TokenUsage
import net.barrage.llmao.core.token.toTokenUsage
import net.barrage.llmao.tables.references.TOKEN_USAGE
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.impl.DSL

class TokenUsageRepositoryRead(private val dslContext: DSLContext) {
  suspend fun getTotalTokenUsageForPeriod(from: KOffsetDateTime, to: KOffsetDateTime): Int {
    val query =
      dslContext
        .select(DSL.sum(TOKEN_USAGE.TOTAL_TOKENS_AMOUNT))
        .from(TOKEN_USAGE)
        .where(TOKEN_USAGE.CREATED_AT.between(from, to))

    return query.awaitSingle().value1().toInt()
  }

  suspend fun listUsage(
    userId: String? = null,
    workflowId: KUUID? = null,
    period: Period = Period.MONTH,
  ): CountedList<TokenUsage> {
    val startDate =
      when (period) {
        Period.WEEK -> KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(6)
        Period.MONTH -> KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusMonths(1)
        Period.YEAR ->
          KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusYears(1).withDayOfMonth(1)
      }

    val conditions =
      userId
        .optionalEq(TOKEN_USAGE.USER_ID)
        .and(workflowId.optionalEq(TOKEN_USAGE.WORKFLOW_ID))
        .and(TOKEN_USAGE.CREATED_AT.between(startDate, KOffsetDateTime.now()))

    val total =
      dslContext.selectCount().from(TOKEN_USAGE).where(conditions).awaitSingle().value1() ?: 0

    val usagePerProvider = mutableMapOf<String, Map<String, List<TokenUsage>>>()
    val usagePerModel = mutableMapOf<String, List<TokenUsage>>()

    val items =
      dslContext.selectFrom(TOKEN_USAGE).where(conditions).asFlow().collect {
        val usage = it.toTokenUsage()

        val provider = usage.provider
        val model = usage.model

      }

    return CountedList(total, items)
  }
}
