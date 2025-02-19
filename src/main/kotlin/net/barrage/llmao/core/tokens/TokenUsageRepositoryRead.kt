package net.barrage.llmao.core.tokens

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.TOKEN_USAGE
import org.jooq.DSLContext
import org.jooq.impl.DSL

class TokenUsageRepositoryRead(private val dslContext: DSLContext) {
  suspend fun getTotalTokenUsageForPeriod(from: KOffsetDateTime, to: KOffsetDateTime): Int {
    val query =
      dslContext
        .select(DSL.sum(TOKEN_USAGE.AMOUNT))
        .from(TOKEN_USAGE)
        .where(TOKEN_USAGE.CREATED_AT.between(from, to))

    return query.awaitSingle().value1().toInt()
  }

  suspend fun listUsage(userId: KUUID? = null, agentId: KUUID? = null): CountedList<TokenUsage> {
    val total =
      dslContext
        .selectCount()
        .from(TOKEN_USAGE)
        .where(
          userId?.let { TOKEN_USAGE.USER_ID.eq(userId) }
            ?: DSL.noCondition()
              .and(agentId?.let { TOKEN_USAGE.AGENT_ID.eq(agentId) } ?: DSL.noCondition())
        )
        .awaitSingle()
        .value1() ?: 0

    val items =
      dslContext
        .selectFrom(TOKEN_USAGE)
        .where(
          userId?.let { TOKEN_USAGE.USER_ID.eq(userId) }
            ?: DSL.noCondition()
              .and(agentId?.let { TOKEN_USAGE.AGENT_ID.eq(agentId) } ?: DSL.noCondition())
        )
        .asFlow()
        .map { it.toTokenUsage() }
        .toList()

    return CountedList(total, items)
  }
}
