package net.barrage.llmao.core.token

import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.TOKEN_USAGE
import org.jooq.DSLContext

class TokenUsageRepositoryWrite(private val dslContext: DSLContext) {
  suspend fun insert(
    userId: String?,
    username: String?,
    agentId: KUUID?,
    origin: String,
    originId: KUUID?,
    amountPrompt: Int?,
    amountCompletion: Int?,
    amountTotal: Int?,
    usageType: TokenUsageType,
    model: String,
    provider: String,
  ) {
    dslContext
      .insertInto(TOKEN_USAGE)
      .columns(
        TOKEN_USAGE.USER_ID,
        TOKEN_USAGE.USERNAME,
        TOKEN_USAGE.AGENT_ID,
        TOKEN_USAGE.ORIGIN,
        TOKEN_USAGE.ORIGIN_ID,
        TOKEN_USAGE.PROMPT_TOKENS_AMOUNT,
        TOKEN_USAGE.COMPLETION_TOKENS_AMOUNT,
        TOKEN_USAGE.TOTAL_TOKENS_AMOUNT,
        TOKEN_USAGE.USAGE_TYPE,
        TOKEN_USAGE.MODEL,
        TOKEN_USAGE.PROVIDER,
      )
      .values(
        userId,
        username,
        agentId,
        origin,
        originId,
        amountPrompt,
        amountCompletion,
        amountTotal,
        usageType.name,
        model,
        provider,
      )
      .awaitSingle()
  }
}
