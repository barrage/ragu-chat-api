package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentToolCallsRecord
import net.barrage.llmao.tables.records.MessagesRecord

@Serializable
data class Message(
  val id: KUUID,
  /**
   * Represents either the user who sent the message or an agent's configuration ID which was used
   * when the agent responded.
   */
  val sender: KUUID,

  /** Can be one of: user, assistant, system. */
  val senderType: String,

  /** Message content. */
  val content: String,

  /** Which chat the message belongs to. */
  val chatId: KUUID,

  /**
   * Can only be present on message where `senderType == assistant`. Which message the agent is
   * replying to.
   */
  val responseTo: KUUID? = null,

  /** :( / :) */
  val evaluation: Boolean? = null,

  /** Evaluation description. */
  val feedback: String? = null,

  /**
   * Why the LLM stopped streaming. If this is anything other than STOP or MANUAL_STOP, an error
   * occurred during inference.
   */
  val finishReason: FinishReason? = null,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun MessagesRecord.toMessage() =
  Message(
    id = this.id!!,
    sender = this.sender,
    senderType = this.senderType,
    content = this.content,
    chatId = this.chatId,
    responseTo = this.responseTo,
    finishReason = this.finishReason?.let { FinishReason(it) },
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

data class MessageInsert(
  val id: KUUID,
  val chatId: KUUID,
  val sender: KUUID,
  val senderType: String,
  val content: String,
  val responseTo: KUUID? = null,
  val finishReason: FinishReason? = null,
)

@Serializable
data class AgentConfigurationEvaluatedMessages(
  val total: Int,
  val positive: Int,
  val negative: Int,
  val evaluatedMessages: CountedList<Message>,
)

/** Represents an agent's request to call a tool, as well as the response for that tool. */
@Serializable
data class AgentToolCall(
  val id: KUUID,
  val messageId: KUUID,
  val toolName: String,
  val toolArguments: String,
  val toolResult: String,
)

fun AgentToolCallsRecord.toAgentToolCall() =
  AgentToolCall(
    id = id!!,
    messageId = messageId,
    toolName = toolName,
    toolArguments = toolArguments,
    toolResult = toolResult,
  )
