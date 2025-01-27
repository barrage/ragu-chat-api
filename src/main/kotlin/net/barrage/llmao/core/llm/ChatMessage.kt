package net.barrage.llmao.core.llm

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.Message

@Serializable
data class MessageChunk(
  val id: String? = null,
  val created: Long,
  val content: String? = null,
  val stopReason: FinishReason? = null,
  val type: String? = null,
)

@Serializable
data class ChatCompletion(
  val id: String,

  /** The creation time in epoch milliseconds. */
  val created: Long,

  /** The model used. */
  val model: String,

  /** A list of generated completions */
  val choices: List<ChatChoice>,
)

@Serializable
data class ChatChoice(
  /** Chat choice index. */
  val index: Int,

  /** The generated chat message. */
  val message: ChatMessage,

  /** The reason why OpenAI stopped generating. */
  val finishReason: FinishReason? = null,
  val delta: ChatChoiceDelta? = null,
)

@Serializable
data class ChatChoiceDelta(
  val index: Int,
  val content: String? = null,
  val stopReason: FinishReason? = null,
  val toolCalls: List<ToolCallData>? = null,
)

/**
 * Data class representing incoming and outgoing chat messages.
 *
 * The `role` and `content` are always present. The `functionCall` and tool calls are present only
 * if the LLM is configured to use function calling and is only expected to be present on incoming
 * messages.
 */
@Serializable
data class ChatMessage(
  val role: String,
  val content: String,
  val toolCalls: List<ToolCallData>? = null,
  val toolCallId: String? = null,
) {
  companion object {
    fun fromModel(model: Message): ChatMessage {
      return ChatMessage(model.senderType, model.content)
    }

    fun user(content: String): ChatMessage {
      return ChatMessage("user", content)
    }

    fun assistant(content: String): ChatMessage {
      return ChatMessage("assistant", content)
    }

    fun system(content: String): ChatMessage {
      return ChatMessage("system", content)
    }
  }
}

/** Mimics the OpenAI finish reason enum, adding additional application specific stop reasons. */
@Serializable
@JvmInline
value class FinishReason(val value: String) {
  companion object {
    // Application specific reasons

    val ManualStop = FinishReason("manual_stop")

    // LLM specific reasons

    val Stop = FinishReason("stop")
    val Length = FinishReason("length")
    val FunctionCall = FinishReason("function_call")
    val ToolCalls = FinishReason("tool_calls")
    val ContentFilter = FinishReason("content_filter")
  }
}
