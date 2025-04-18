package net.barrage.llmao.app.workflow.jirakira

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.tables.references.JIRA_API_KEYS
import net.barrage.llmao.tables.references.JIRA_WORKLOG_ATTRIBUTES
import org.jooq.DSLContext

class JiraKiraRepository(private val dslContext: DSLContext) : JiraKiraKeyStore {
  override suspend fun setUserApiKey(userId: String, apiKey: String) {
    dslContext
      .insertInto(JIRA_API_KEYS)
      .set(JIRA_API_KEYS.USER_ID, userId)
      .set(JIRA_API_KEYS.API_KEY, apiKey)
      .awaitFirstOrNull()
  }

  override suspend fun getUserApiKey(userId: String): String? {
    return dslContext
      .select(JIRA_API_KEYS.API_KEY)
      .from(JIRA_API_KEYS)
      .where(JIRA_API_KEYS.USER_ID.eq(userId))
      .awaitFirstOrNull()
      ?.into(JIRA_API_KEYS)
      ?.apiKey
  }

  override suspend fun removeUserApiKey(userId: String) {
    dslContext.deleteFrom(JIRA_API_KEYS).where(JIRA_API_KEYS.USER_ID.eq(userId)).awaitFirstOrNull()
  }

  suspend fun listAllWorklogAttributes(): List<WorklogAttribute> {
    return dslContext
      .select(
        JIRA_WORKLOG_ATTRIBUTES.ID,
        JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION,
        JIRA_WORKLOG_ATTRIBUTES.REQUIRED,
      )
      .from(JIRA_WORKLOG_ATTRIBUTES)
      .asFlow()
      .map { it.into(JIRA_WORKLOG_ATTRIBUTES).toWorklogAttributeModel() }
      .toList()
  }

  suspend fun upsertWorklogAttribute(id: String, description: String, required: Boolean) {
    dslContext
      .insertInto(JIRA_WORKLOG_ATTRIBUTES)
      .set(JIRA_WORKLOG_ATTRIBUTES.ID, id)
      .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
      .set(JIRA_WORKLOG_ATTRIBUTES.REQUIRED, required)
      .onConflict(JIRA_WORKLOG_ATTRIBUTES.ID)
      .doUpdate()
      .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
      .set(JIRA_WORKLOG_ATTRIBUTES.REQUIRED, required)
      .awaitFirstOrNull()
  }

  suspend fun removeWorklogAttribute(id: String) {
    dslContext
      .deleteFrom(JIRA_WORKLOG_ATTRIBUTES)
      .where(JIRA_WORKLOG_ATTRIBUTES.ID.eq(id))
      .awaitFirstOrNull()
  }
}
