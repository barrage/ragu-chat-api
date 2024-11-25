package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
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
    evaluation = this.evaluation,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

@Serializable
data class AgentConfigurationEvaluatedMessages(
  val total: Int,
  val positive: Int,
  val negative: Int,
  val evaluatedMessages: CountedList<Message>,
)

/** Mimics the OpenAI finish reason enum, adding additional application specific stop reasons. */
@Serializable
@JvmInline
value class FinishReason(val value: String) {
  companion object {
    val ManualStop = FinishReason("manual_stop")
    val Stop = FinishReason("stop")
    val Length = FinishReason("length")
    val FunctionCall = FinishReason("function_call")
    val ToolCalls = FinishReason("tool_calls")
    val ContentFilter = FinishReason("content_filter")
  }

  fun from(value: com.aallam.openai.api.core.FinishReason): FinishReason {
    return when (value) {
      com.aallam.openai.api.core.FinishReason.Stop -> Stop
      com.aallam.openai.api.core.FinishReason.Length -> Length
      com.aallam.openai.api.core.FinishReason.FunctionCall -> FunctionCall
      com.aallam.openai.api.core.FinishReason.ToolCalls -> ToolCalls
      com.aallam.openai.api.core.FinishReason.ContentFilter -> ContentFilter
      else -> TODO()
    }
  }
}
