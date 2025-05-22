package net.barrage.llmao.core.repository

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.toMessage
import net.barrage.llmao.core.model.toMessageAttachment
import net.barrage.llmao.core.model.toMessageGroup
import net.barrage.llmao.core.model.toMessageGroupEvaluation
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_ATTACHMENTS
import net.barrage.llmao.tables.references.MESSAGE_GROUPS
import net.barrage.llmao.tables.references.MESSAGE_GROUP_EVALUATIONS
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

/** Implement on classes that need to track workflow messages. */
interface MessageRepository {
  val dslContext: DSLContext

  /**
   * Utility function for inserting a list of messages associated with the given workflow.
   *
   * @param workflowId The workflow ID.
   * @param workflowType The workflow type.
   * @param messages The messages to insert.
   * @param dsl Optional transaction context in which to execute.
   * @return The ID of the **message group** that was created.
   */
  suspend fun insertWorkflowMessages(
    workflowId: KUUID,
    workflowType: String,
    messages: List<MessageInsert>,
    dsl: DSLContext? = null,
  ): KUUID {
    return if (dsl == null) {
      dslContext.transactionCoroutine { tx ->
        tx.dsl().insertWorkflowMessages(workflowId, workflowType, messages)
      }
    } else {
      dsl.insertWorkflowMessages(workflowId, workflowType, messages)
    }
  }

  /**
   * List message groups for a workflow. If pagination is not provided, all message groups are
   * listed.
   */
  suspend fun getWorkflowMessages(
    workflowId: KUUID,
    pagination: Pagination? = null,
  ): CountedList<MessageGroupAggregate> {
    val messageGroups = mutableMapOf<KUUID, MessageGroupAggregate>()

    val total =
      dslContext
        .selectCount()
        .from(MESSAGE_GROUPS)
        .where(MESSAGE_GROUPS.PARENT_ID.eq(workflowId))
        .awaitSingle()
        .value1() ?: 0

    // This query acts like a window, selecting only the latest
    // message groups
    val subQuery =
      dslContext
        .select(MESSAGE_GROUPS.ID)
        .from(MESSAGE_GROUPS)
        .where(MESSAGE_GROUPS.PARENT_ID.eq(workflowId))
        .orderBy(MESSAGE_GROUPS.CREATED_AT.desc())

    val paginatedSubQuery =
      pagination?.let { p ->
        val (limit, offset) = p.limitOffset()
        subQuery.limit(limit).offset(offset)
      } ?: subQuery

    dslContext
      .select(
        // Group
        MESSAGE_GROUPS.ID,
        MESSAGE_GROUPS.PARENT_ID,
        MESSAGE_GROUPS.PARENT_TYPE,
        MESSAGE_GROUPS.CREATED_AT,
        // Evaluation
        MESSAGE_GROUP_EVALUATIONS.ID,
        MESSAGE_GROUP_EVALUATIONS.EVALUATION,
        MESSAGE_GROUP_EVALUATIONS.FEEDBACK,
        MESSAGE_GROUP_EVALUATIONS.CREATED_AT,
        MESSAGE_GROUP_EVALUATIONS.UPDATED_AT,
        // Message
        MESSAGES.ID,
        MESSAGES.ORDER,
        MESSAGES.MESSAGE_GROUP_ID,
        MESSAGES.SENDER_TYPE,
        MESSAGES.CONTENT,
        MESSAGES.TOOL_CALLS,
        MESSAGES.TOOL_CALL_ID,
        MESSAGES.FINISH_REASON,
        MESSAGES.CREATED_AT,
      )
      .from(MESSAGE_GROUPS)
      .leftJoin(MESSAGE_GROUP_EVALUATIONS)
      .on(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
      .leftJoin(MESSAGES)
      .on(MESSAGES.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
      .where(MESSAGE_GROUPS.ID.`in`(paginatedSubQuery))
      .orderBy(MESSAGE_GROUPS.CREATED_AT.asc(), MESSAGES.ORDER.asc())
      .asFlow()
      .collect { record ->
        val messageId = record.get(MESSAGES.ID)!!

        val attachments =
          dslContext
            .selectFrom(MESSAGE_ATTACHMENTS)
            .where(MESSAGE_ATTACHMENTS.MESSAGE_ID.eq(messageId))
            .asFlow()
            .map { it.into(MESSAGE_ATTACHMENTS).toMessageAttachment() }
            .toList()

        val message =
          record.into(MESSAGES).toMessage(if (attachments.isEmpty()) null else attachments)
        val group = record.into(MESSAGE_GROUPS).toMessageGroup()
        val evaluation =
          record.into(MESSAGE_GROUP_EVALUATIONS).let { eval ->
            eval.id?.let { eval.toMessageGroupEvaluation() }
          }

        messageGroups
          .computeIfAbsent(group.id) { _ ->
            MessageGroupAggregate(group, mutableListOf(), evaluation)
          }
          .messages
          .add(message)
      }

    return CountedList(total, messageGroups.values.toList())
  }

  suspend fun getMessageGroupAggregate(id: KUUID): MessageGroupAggregate? {
    return dslContext
      .select(
        MESSAGE_GROUPS.ID,
        MESSAGE_GROUPS.PARENT_ID,
        MESSAGE_GROUPS.PARENT_TYPE,
        MESSAGE_GROUPS.CREATED_AT,
        MESSAGE_GROUP_EVALUATIONS.ID,
        MESSAGE_GROUP_EVALUATIONS.EVALUATION,
        MESSAGE_GROUP_EVALUATIONS.FEEDBACK,
        MESSAGE_GROUP_EVALUATIONS.CREATED_AT,
        MESSAGE_GROUP_EVALUATIONS.UPDATED_AT,
        MESSAGES.ID,
        MESSAGES.ORDER,
        MESSAGES.MESSAGE_GROUP_ID,
        MESSAGES.SENDER_TYPE,
        MESSAGES.CONTENT,
        MESSAGES.TOOL_CALLS,
        MESSAGES.TOOL_CALL_ID,
        MESSAGES.FINISH_REASON,
        MESSAGES.CREATED_AT,
      )
      .from(MESSAGE_GROUPS)
      .leftJoin(MESSAGE_GROUP_EVALUATIONS)
      .on(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
      .leftJoin(MESSAGES)
      .on(MESSAGES.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
      .where(MESSAGE_GROUPS.ID.eq(id))
      .orderBy(MESSAGES.ORDER.asc())
      .awaitFirstOrNull()
      ?.let { record ->
        val messageId = record.get(MESSAGES.ID)!!

        val attachments =
          dslContext
            .selectFrom(MESSAGE_ATTACHMENTS)
            .where(MESSAGE_ATTACHMENTS.MESSAGE_ID.eq(messageId))
            .asFlow()
            .map { it.into(MESSAGE_ATTACHMENTS).toMessageAttachment() }
            .toList()

        val message = record.into(MESSAGES).toMessage(attachments)
        val group = record.into(MESSAGE_GROUPS).toMessageGroup()
        val evaluation =
          record.into(MESSAGE_GROUP_EVALUATIONS).let { eval ->
            eval.id?.let { eval.toMessageGroupEvaluation() }
          }

        MessageGroupAggregate(group, mutableListOf(message), evaluation)
      }
  }
}

private suspend fun DSLContext.insertWorkflowMessages(
  workflowId: KUUID,
  workflowType: String,
  messages: List<MessageInsert>,
): KUUID {
  val messageGroupId =
    insertInto(MESSAGE_GROUPS)
      .set(MESSAGE_GROUPS.PARENT_ID, workflowId)
      .set(MESSAGE_GROUPS.PARENT_TYPE, workflowType)
      .returning(MESSAGE_GROUPS.ID)
      .awaitSingle()
      .id

  messages.forEachIndexed { index, message ->
    val messageId =
      insertInto(MESSAGES)
        .set(MESSAGES.ORDER, index)
        .set(MESSAGES.MESSAGE_GROUP_ID, messageGroupId)
        .set(MESSAGES.SENDER_TYPE, message.senderType)
        .set(MESSAGES.CONTENT, message.content)
        .set(MESSAGES.FINISH_REASON, message.finishReason?.value)
        .set(MESSAGES.TOOL_CALLS, message.toolCalls?.let { Json.encodeToString(it) })
        .set(MESSAGES.TOOL_CALL_ID, message.toolCallId)
        .returning(MESSAGES.ID)
        .awaitFirstOrNull()
        ?.id ?: throw AppError.internal("Failed to insert message")

    message.attachments?.forEach { attachment ->
      insertInto(MESSAGE_ATTACHMENTS)
        .set(MESSAGE_ATTACHMENTS.MESSAGE_ID, messageId)
        .set(MESSAGE_ATTACHMENTS.TYPE, attachment.type.name)
        .set(MESSAGE_ATTACHMENTS.PROVIDER, attachment.provider)
        .set(MESSAGE_ATTACHMENTS.ORDER, attachment.order)
        .set(MESSAGE_ATTACHMENTS.URL, attachment.url)
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert attachment")
    }
  }

  return messageGroupId as KUUID
}
