package net.barrage.llmao.core.database

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_ATTACHMENTS
import net.barrage.llmao.tables.references.MESSAGE_GROUPS
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateSetMoreStep
import org.jooq.InsertSetMoreStep
import org.jooq.Record
import org.jooq.TableField
import org.jooq.UpdateSetMoreStep
import org.jooq.UpdateSetStep
import org.jooq.impl.DSL.excluded

/**
 * Utility function for inserting a list of messages associated with the given workflow.
 *
 * @return The ID of the message group that was created.
 */
suspend fun DSLContext.insertMessages(workflowId: KUUID, messages: List<MessageInsert>): KUUID {
  val messageGroupId =
    insertInto(MESSAGE_GROUPS)
      .set(MESSAGE_GROUPS.PARENT_ID, workflowId)
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

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Semantics are defined in [PropertyUpdate].
 */
fun <R : Record, T> UpdateSetMoreStep<R>.set(
  update: PropertyUpdate<T>,
  field: TableField<R, T>,
): UpdateSetMoreStep<R> {
  return when (update) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> this

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, update.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

/** The same as a regular `set` but with a remapping function. */
fun <I, R : Record, T> UpdateSetMoreStep<R>.set(
  update: PropertyUpdate<I>,
  field: TableField<R, T>,
  remap: (I) -> T,
): UpdateSetMoreStep<R> {
  return when (update) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> this

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, remap(update.value))

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Semantics are defined in [PropertyUpdate].
 */
fun <R : Record, T> UpdateSetStep<R>.set(
  update: PropertyUpdate<T>,
  field: TableField<R, T>,
): UpdateSetMoreStep<R> {
  return when (update) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> this as UpdateSetMoreStep<R>

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, update.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

fun <R : Record, T> InsertOnDuplicateSetMoreStep<R>.set(
  update: PropertyUpdate<T>,
  field: TableField<R, T>,
): InsertOnDuplicateSetMoreStep<R> {
  return when (update) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> set(field, excluded(field))

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, update.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

fun <R : Record, T> InsertSetMoreStep<R>.set(
  value: PropertyUpdate<T>,
  field: TableField<R, T>,
  defaultIfUndefined: T? = null,
): InsertSetMoreStep<R> {
  return when (value) {
    // Do nothing when property is not set
    is PropertyUpdate.Undefined -> defaultIfUndefined?.let { set(field, it) } ?: this

    // Property is being updated to new value
    is PropertyUpdate.Value -> set(field, value.value)

    // Property is being removed
    is PropertyUpdate.Null -> setNull(field)
  }
}

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <R : Record, T> UpdateSetStep<R>.set(
  update: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): UpdateSetMoreStep<R> =
  update?.let { set(field, it) }
    ?: defaultIfNull?.let { set(field, it) }
    ?: this as UpdateSetMoreStep<R>

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <U, R : Record, T> UpdateSetStep<R>.set(
  update: U?,
  field: TableField<R, T>,
  remap: (U) -> T,
): UpdateSetMoreStep<R> = update?.let { set(field, remap(it)) } ?: this as UpdateSetMoreStep<R>

/**
 * Utility for including a SET statement in a DSLContext update statement.
 *
 * Implementation for *required* properties.
 *
 * If the value is null, leaves the statement as is.
 */
fun <R : Record, T> UpdateSetMoreStep<R>.set(
  update: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): UpdateSetMoreStep<R> =
  update?.let { set(field, it) } ?: defaultIfNull?.let { set(field, it) } ?: this

/**
 * Implementation for *required* properties.
 *
 * If the value is null, tries to set the default value. If the default value is null, leaves the
 * statement as is.
 */
fun <R : Record, T> InsertSetMoreStep<R>.set(
  value: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): InsertSetMoreStep<R> =
  value?.let { set(field, it) } ?: defaultIfNull?.let { set(field, it) } ?: this

/**
 * Implementation for *required* properties.
 *
 * If the value is null, tries to set the default value. If the default value is null, leaves the
 * value as is.
 */
fun <R : Record, T> InsertOnDuplicateSetMoreStep<R>.set(
  value: T?,
  field: TableField<R, T>,
  defaultIfNull: T? = null,
): InsertOnDuplicateSetMoreStep<R> =
  value?.let { set(field, it) }
    ?: defaultIfNull?.let { set(field, it) }
    ?: set(field, excluded(field))
