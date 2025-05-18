package net.barrage.llmao.core.repository

import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.token.TokenUsageType
import net.barrage.llmao.tables.references.TOKEN_USAGE
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext

class TokenUsageRepositoryWrite(private val dslContext: DSLContext) {
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
