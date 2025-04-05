package net.barrage.llmao.core.specialist

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.SpecialistWorkflows.Companion.SPECIALIST_WORKFLOWS
import net.barrage.llmao.tables.references.SPECIALIST_MESSAGES
import net.barrage.llmao.tables.references.SPECIALIST_MESSAGE_GROUPS
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class SpecialistRepositoryWrite(private val dslContext: DSLContext, private val type: String) {
  suspend fun insertWorkflowWithMessages(
    workflowId: KUUID,
    userId: String,
    username: String?,
    messages: List<MessageInsert>,
  ): KUUID =
    dslContext.transactionCoroutine { ctx ->
      ctx.dsl().insertWorkflow(workflowId, userId, username, type)
      ctx.dsl().insertMessages(workflowId, messages)
    }

  suspend fun insertMessages(workflowId: KUUID, messages: List<MessageInsert>): KUUID =
    dslContext.insertMessages(workflowId, messages)
}

private suspend fun DSLContext.insertWorkflow(
  workflowId: KUUID,
  userId: String,
  username: String?,
  type: String,
) {
  insertInto(SPECIALIST_WORKFLOWS)
    .set(SPECIALIST_WORKFLOWS.ID, workflowId)
    .set(SPECIALIST_WORKFLOWS.USER_ID, userId)
    .set(SPECIALIST_WORKFLOWS.USERNAME, username)
    .set(SPECIALIST_WORKFLOWS.TYPE, type)
    .awaitSingle()
}

private suspend fun DSLContext.insertMessages(
  workflowId: KUUID,
  messages: List<MessageInsert>,
): KUUID {
  val messageGroupId =
    insertInto(SPECIALIST_MESSAGE_GROUPS)
      .set(SPECIALIST_MESSAGE_GROUPS.WORKFLOW_ID, workflowId)
      .returning(SPECIALIST_MESSAGE_GROUPS.ID)
      .awaitFirstOrNull()
      ?.id ?: throw AppError.internal("Failed to insert message group")

  messages.forEachIndexed { index, message ->
    insertInto(SPECIALIST_MESSAGES)
      .set(SPECIALIST_MESSAGES.ORDER, index)
      .set(SPECIALIST_MESSAGES.MESSAGE_GROUP_ID, messageGroupId)
      .set(SPECIALIST_MESSAGES.SENDER_TYPE, message.senderType)
      .set(SPECIALIST_MESSAGES.CONTENT, message.content)
      .set(SPECIALIST_MESSAGES.FINISH_REASON, message.finishReason?.value)
      .set(SPECIALIST_MESSAGES.TOOL_CALLS, message.toolCalls?.let { Json.encodeToString(it) })
      .set(SPECIALIST_MESSAGES.TOOL_CALL_ID, message.toolCallId)
      .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message")
  }

  return messageGroupId
}
