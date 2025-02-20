package net.barrage.llmao.app.workflow.jirakira

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.tables.references.JIRA_API_KEYS
import net.barrage.llmao.tables.references.JIRA_KIRA_MESSAGES
import net.barrage.llmao.tables.references.JIRA_KIRA_WORKFLOWS
import net.barrage.llmao.tables.references.JIRA_WORKLOG_ATTRIBUTES
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class JiraKiraRepository(private val dslContext: DSLContext) : JiraKiraKeyStore {
  override suspend fun setUserApiKey(userId: KUUID, apiKey: String) {
    dslContext
      .insertInto(JIRA_API_KEYS)
      .set(JIRA_API_KEYS.USER_ID, userId)
      .set(JIRA_API_KEYS.API_KEY, apiKey)
      .awaitFirstOrNull()
  }

  override suspend fun getUserApiKey(userId: KUUID): String? {
    return dslContext
      .select(JIRA_API_KEYS.API_KEY)
      .from(JIRA_API_KEYS)
      .where(JIRA_API_KEYS.USER_ID.eq(userId))
      .awaitFirstOrNull()
      ?.into(JIRA_API_KEYS)
      ?.apiKey
  }

  override suspend fun removeUserApiKey(userId: KUUID) {
    dslContext.deleteFrom(JIRA_API_KEYS).where(JIRA_API_KEYS.USER_ID.eq(userId)).awaitFirstOrNull()
  }

  suspend fun listAllWorklogAttributes(): List<WorklogAttribute> {
    return dslContext
      .select(JIRA_WORKLOG_ATTRIBUTES.ID, JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION)
      .from(JIRA_WORKLOG_ATTRIBUTES)
      .asFlow()
      .map { it.into(JIRA_WORKLOG_ATTRIBUTES).toWorklogAttributeModel() }
      .toList()
  }

  suspend fun upsertWorklogAttribute(id: String, description: String) {
    dslContext
      .insertInto(JIRA_WORKLOG_ATTRIBUTES)
      .set(JIRA_WORKLOG_ATTRIBUTES.ID, id)
      .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
      .onConflict(JIRA_WORKLOG_ATTRIBUTES.ID)
      .doUpdate()
      .set(JIRA_WORKLOG_ATTRIBUTES.DESCRIPTION, description)
      .awaitFirstOrNull()
  }

  suspend fun removeWorklogAttribute(id: String) {
    dslContext
      .deleteFrom(JIRA_WORKLOG_ATTRIBUTES)
      .where(JIRA_WORKLOG_ATTRIBUTES.ID.eq(id))
      .awaitFirstOrNull()
  }

  suspend fun insertJiraKiraWorkflow(workflowId: KUUID, userId: KUUID) {
    dslContext
      .dsl()
      .insertInto(JIRA_KIRA_WORKFLOWS)
      .set(JIRA_KIRA_WORKFLOWS.ID, workflowId)
      .set(JIRA_KIRA_WORKFLOWS.USER_ID, userId)
      .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert workflow")
  }

  suspend fun insertJiraKiraMessage(message: JiraKiraMessageInsert): KUUID {
    return dslContext
      .insertInto(JIRA_KIRA_MESSAGES)
      .set(JIRA_KIRA_MESSAGES.WORKFLOW_ID, message.workflowId)
      .set(JIRA_KIRA_MESSAGES.SENDER, message.sender)
      .set(JIRA_KIRA_MESSAGES.SENDER_TYPE, message.senderType)
      .set(JIRA_KIRA_MESSAGES.TOOL_CALLS, message.toolCalls)
      .set(JIRA_KIRA_MESSAGES.CONTENT, message.content)
      .set(JIRA_KIRA_MESSAGES.RESPONSE_TO, message.responseTo)
      .returning(JIRA_KIRA_MESSAGES.ID)
      .awaitFirstOrNull()!!
      .id!!
  }

  suspend fun insertJiraKiraMessages(messages: List<JiraKiraMessageInsert>) {
    return dslContext.transactionCoroutine { ctx ->
      for (message in messages) {
        ctx
          .dsl()
          .insertInto(JIRA_KIRA_MESSAGES)
          .set(JIRA_KIRA_MESSAGES.WORKFLOW_ID, message.workflowId)
          .set(JIRA_KIRA_MESSAGES.SENDER, message.sender)
          .set(JIRA_KIRA_MESSAGES.SENDER_TYPE, message.senderType)
          .set(JIRA_KIRA_MESSAGES.TOOL_CALLS, message.toolCalls)
          .set(JIRA_KIRA_MESSAGES.CONTENT, message.content)
          .set(JIRA_KIRA_MESSAGES.RESPONSE_TO, message.responseTo)
          .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message")
      }
    }
  }
}
