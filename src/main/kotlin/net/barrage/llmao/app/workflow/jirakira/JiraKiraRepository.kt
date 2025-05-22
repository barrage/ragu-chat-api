package net.barrage.llmao.app.workflow.jirakira

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.repository.MessageRepository
import net.barrage.llmao.tables.references.JIRAKIRA_WORKFLOWS
import net.barrage.llmao.tables.references.JIRA_API_KEYS
import net.barrage.llmao.tables.references.JIRA_WORKLOG_ATTRIBUTES
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class JiraKiraRepository(override val dslContext: DSLContext) :
  JiraKiraKeyStore, MessageRepository {
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

  suspend fun insertWorkflowWithMessages(
    workflowId: KUUID,
    userId: String,
    username: String?,
    messages: List<MessageInsert>,
  ): KUUID =
    dslContext.transactionCoroutine { ctx ->
      ctx
        .dsl()
        .insertInto(JIRAKIRA_WORKFLOWS)
        .set(JIRAKIRA_WORKFLOWS.ID, workflowId)
        .set(JIRAKIRA_WORKFLOWS.USER_ID, userId)
        .set(JIRAKIRA_WORKFLOWS.USERNAME, username)
        .awaitSingle()
      insertWorkflowMessages(workflowId, JIRAKIRA_WORKFLOW_ID, messages, ctx.dsl())
    }

  suspend fun insertMessages(workflowId: KUUID, messages: List<MessageInsert>): KUUID =
    insertWorkflowMessages(workflowId, JIRAKIRA_WORKFLOW_ID, messages)
}
