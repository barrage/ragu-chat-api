package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.MessageGroupEvaluationsRecord
import net.barrage.llmao.tables.records.MessageGroupsRecord
import net.barrage.llmao.tables.records.MessagesRecord

/** TABLE: chats */
@Serializable
data class Chat(
  val id: KUUID,
  val agentId: KUUID,

  /**
   * The ID of the user who created the chat. Used to link to the user's account on the auth server.
   */
  val userId: String,

  /** Username at the time of chat creation. Used for display purposes. */
  val username: String?,

  /** Optional title. Certain chat implementations can have auto-generated titles. */
  val title: String?,

  /**
   * The base model holds the type as a string. Implementations should take care to use enumerated
   * values.
   */
  val type: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

/**
 * TABLE: message_groups
 *
 * Used to bundle interactions together. Since any amount of tool calls can happen in between a user
 * and assistant message, they need to be grouped.
 *
 * This also ensures consistency when fetching the messages.
 */
@Serializable
data class MessageGroup(
  val id: KUUID,

  /** The chat ID this message group belongs to. */
  val chatId: KUUID,

  /** The agent configuration at the time of interaction. */
  val agentConfigurationId: KUUID,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun MessageGroupsRecord.toMessageGroup() =
  MessageGroup(
    id = this.id!!,
    chatId = this.chatId,
    agentConfigurationId = this.agentConfigurationId,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
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

  /** :( / :) */
  // val evaluation: Boolean? = null,

  /** Evaluation description. */
  // val feedback: String? = null,
  /** Tools called by the assistant. If this is present, the content is _most likely_ null. */
  val toolCalls: String?,

  /**
   * The tool call ID, representing which call this message is a response to. Only present on tool
   * messages.
   */
  val toolCallId: String?,

  /**
   * Why the LLM stopped streaming. If this is anything other than STOP or MANUAL_STOP, an error
   * occurred during inference.
   */
  val finishReason: FinishReason? = null,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
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
@Serializable data class EvaluateMessage(val evaluation: Boolean?, val feedback: String? = null)

@Serializable
data class AgentConfigurationEvaluatedMessages(
  val total: Int,
  val positive: Int,
  val negative: Int,
  val evaluatedMessages: CountedList<Message>,
)

/**
 * Aggregate of the message group and all the messages within it, along with the group's evaluation.
 */
@Serializable
data class MessageGroupAggregate(
  val group: MessageGroup,
  val messages: MutableList<Message>,
  val evaluation: MessageGroupEvaluation?,
)

fun MessagesRecord.toMessage() =
  Message(
    id = this.id!!,
    senderType = this.senderType,
    content = this.content,
    messageGroupId = this.messageGroupId,
    finishReason = this.finishReason?.let { FinishReason(it) },
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    order = this.order,
    toolCalls = this.toolCalls,
    toolCallId = this.toolCallId,
  )

data class MessageInsert(
  val id: KUUID,
  val senderType: String,
  val content: String?,
  val finishReason: FinishReason? = null,
  val toolCalls: List<ToolCallData>? = null,
  val toolCallId: String? = null,
)

@Serializable
data class ChatDTO(val chat: Chat, val messageGroups: List<MessageGroup>?, val agent: Agent?)

/** Base model with its messages. */
@Serializable
data class ChatWithMessages(val chat: Chat, val messages: CountedList<MessageGroupAggregate>)

@Serializable data class ChatWithAgent(val chat: Chat, val agent: Agent)

fun ChatsRecord.toChat() =
  Chat(
    id = this.id!!,
    userId = this.userId,
    agentId = this.agentId,
    username = this.username,
    title = this.title,
    type = this.type,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
