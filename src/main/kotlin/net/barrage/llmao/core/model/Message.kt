package net.barrage.llmao.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.tables.records.MessageAttachmentsRecord
import net.barrage.llmao.tables.records.MessageGroupEvaluationsRecord
import net.barrage.llmao.tables.records.MessageGroupsRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

/**
 * TABLE: message_groups
 *
 * Used to bundle interactions together. Since any amount of tool calls can happen in between a user
 * and assistant message, they need to be grouped.
 *
 * This also ensures consistency when fetching the messages.
 */
@Serializable
open class MessageGroup(
  val id: KUUID,

  /** The workflow ID this message group belongs to. */
  val parentId: KUUID,
  val createdAt: KOffsetDateTime,
)

fun MessageGroupsRecord.toMessageGroup() =
  MessageGroup(id = this.id!!, parentId = this.parentId, createdAt = this.createdAt!!)

/**
 * Aggregate of the message group and all the messages within it, along with the group's evaluation.
 */
@Serializable
data class MessageGroupAggregate(
  val group: MessageGroup,
  val messages: MutableList<Message>,
  val evaluation: MessageGroupEvaluation?,
)

/** TABLE: message_group_evaluations */
@Serializable
data class MessageGroupEvaluation(
  val id: KUUID,
  val messageGroupId: KUUID,
  val evaluation: Boolean,
  val feedback: String?,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun MessageGroupEvaluationsRecord.toMessageGroupEvaluation() =
  MessageGroupEvaluation(
    id = this.id!!,
    messageGroupId = this.messageGroupId,
    evaluation = this.evaluation,
    feedback = this.feedback,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

/** DTO for evaluating a message. */
@Serializable
data class EvaluateMessage(
  val evaluation: Boolean? = null,
  val feedback: PropertyUpdate<String> = PropertyUpdate.Undefined,
)

/** TABLE: messages */
@Serializable
data class Message(
  val id: KUUID,

  /**
   * Specific to the message group the message belongs to. Represents the order the message was
   * sent.
   */
  val order: Int,

  /** Which message group, and ultimately the chat, this message belongs to. */
  val messageGroupId: KUUID,

  /** Can be one of: user, assistant, system, tool. */
  val senderType: String,

  /** Message content. Always present on user messages and final assistant messages. */
  val content: String?,

  /** Tools called by the assistant. If this is present, the content is _most likely_ null. */
  val toolCalls: String?,

  /**
   * The tool call ID, representing which call this message is a response to. Only present on tool
   * messages.
   */
  val toolCallId: String?,

  /** Why the LLM stopped streaming. */
  val finishReason: FinishReason? = null,

  /** Additional binary data sent with the message. */
  val attachments: List<MessageAttachment>? = null,
  val createdAt: KOffsetDateTime?,
)

/**
 * TABLE: message_attachments
 *
 * Additional binary data sent with messages.
 */
@Serializable
data class MessageAttachment(
  /** Type of binary data. */
  val type: MessageAttachmentType,

  /**
   * The origin of the binary data.
   *
   * If this is `null`, the URL is a public URL and no provider is required to obtain the data as
   * the client will load it.
   */
  val provider: String?,

  /** The order in which the attachment was sent. */
  val order: Int,

  /** URL specific to the provider, or a public URL if no provider is required. */
  val url: String,
)

/** Type of attachment. */
@Serializable
enum class MessageAttachmentType {
  /** The attachment is a URL and requires no loading from a provider. */
  IMAGE_URL,

  /**
   * The attachment a base64 encoded image and requires loading from a provider, the URL will be a
   * path specific to the provider.
   */
  IMAGE_RAW,
}

/** Incoming attachments in messages before they are processed. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class IncomingMessageAttachment {
  @Serializable
  @SerialName("image")
  data class Image(val data: IncomingImageData) : IncomingMessageAttachment()
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class IncomingImageData {
  /** Raw binary data. */
  @Serializable @SerialName("raw") data class Raw(val data: String) : IncomingImageData()

  /** URL to obtain the image. */
  @Serializable @SerialName("url") data class Url(val url: String) : IncomingImageData()
}

fun MessagesRecord.toMessage(attachments: List<MessageAttachment>? = null) =
  Message(
    id = this.id!!,
    senderType = this.senderType,
    content = this.content,
    messageGroupId = this.messageGroupId,
    finishReason = this.finishReason?.let { FinishReason(it) },
    order = this.order,
    toolCalls = this.toolCalls,
    toolCallId = this.toolCallId,
    attachments = attachments,
    createdAt = this.createdAt,
  )

fun MessageAttachmentsRecord.toMessageAttachment() =
  MessageAttachment(
    type = MessageAttachmentType.valueOf(this.type),
    provider = this.provider,
    order = this.order,
    url = this.url,
  )

data class MessageInsert(
  val id: KUUID,
  val senderType: String,
  val content: String?,
  val finishReason: FinishReason? = null,
  val toolCalls: List<ToolCallData>? = null,
  val toolCallId: String? = null,
  val attachments: List<MessageAttachment>? = null,
)
