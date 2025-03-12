package net.barrage.llmao.core.repository

import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.core.models.SpecialistMessageInsert
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.tables.SpecialistWorkflows.Companion.SPECIALIST_WORKFLOWS
import net.barrage.llmao.tables.references.SPECIALIST_MESSAGES
import net.barrage.llmao.tables.references.SPECIALIST_MESSAGE_GROUPS
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class SpecialistRepositoryWrite(private val dslContext: DSLContext, private val type: String) {
  suspend fun insertWorkflow(workflowId: KUUID, userId: String, username: String?) {
    dslContext
      .dsl()
      .insertInto(SPECIALIST_WORKFLOWS)
      .set(SPECIALIST_WORKFLOWS.ID, workflowId)
      .set(SPECIALIST_WORKFLOWS.USER_ID, userId)
      .set(SPECIALIST_WORKFLOWS.USERNAME, username)
      .set(SPECIALIST_WORKFLOWS.TYPE, type)
      .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert workflow")
  }

  suspend fun insertMessages(workflowId: KUUID, messages: List<SpecialistMessageInsert>) {
    return dslContext.transactionCoroutine { ctx ->
      val messageGroupId =
        ctx
          .dsl()
          .insertInto(SPECIALIST_MESSAGE_GROUPS)
          .set(SPECIALIST_MESSAGE_GROUPS.WORKFLOW_ID, workflowId)
          .returning(SPECIALIST_MESSAGE_GROUPS.ID)
          .awaitFirstOrNull()
          ?.id ?: throw AppError.internal("Failed to insert message group")

      messages.forEachIndexed { index, message ->
        ctx
          .dsl()
          .insertInto(SPECIALIST_MESSAGES)
          .set(SPECIALIST_MESSAGES.ORDER, index)
          .set(SPECIALIST_MESSAGES.MESSAGE_GROUP_ID, messageGroupId)
          .set(SPECIALIST_MESSAGES.SENDER_TYPE, message.senderType)
          .set(SPECIALIST_MESSAGES.CONTENT, message.content)
          .set(SPECIALIST_MESSAGES.FINISH_REASON, message.finishReason?.value)
          .set(SPECIALIST_MESSAGES.TOOL_CALLS, message.toolCalls)
          .set(SPECIALIST_MESSAGES.TOOL_CALL_ID, message.toolCallId)
          .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message")
      }
    }
  }
}
