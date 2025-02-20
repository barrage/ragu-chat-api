package net.barrage.llmao.core.tokens

import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.TOKEN_USAGE
import org.jooq.DSLContext

class TokenUsageRepositoryWrite(private val dslContext: DSLContext) {
  suspend fun insert(
    userId: KUUID?,
    agentId: KUUID?,
    agentConfigurationId: KUUID?,
    origin: String,
    originId: KUUID?,
    amount: Int,
    usageType: TokenUsageType,
    model: String,
    provider: String,
  ) {
    dslContext
      .insertInto(TOKEN_USAGE)
      .columns(
        TOKEN_USAGE.USER_ID,
        TOKEN_USAGE.AGENT_ID,
        TOKEN_USAGE.AGENT_CONFIGURATION_ID,
        TOKEN_USAGE.ORIGIN,
        TOKEN_USAGE.ORIGIN_ID,
        TOKEN_USAGE.AMOUNT,
        TOKEN_USAGE.USAGE_TYPE,
        TOKEN_USAGE.MODEL,
        TOKEN_USAGE.PROVIDER,
      )
      .values(
        userId,
        agentId,
        agentConfigurationId,
        origin,
        originId,
        amount,
        usageType.name,
        model,
        provider,
      )
      .awaitSingle()
  }
}
