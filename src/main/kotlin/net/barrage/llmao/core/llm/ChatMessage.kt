package net.barrage.llmao.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.MessageInsert
import net.barrage.llmao.core.tokens.TokenUsageAmount
import net.barrage.llmao.core.types.KUUID

@Serializable
data class ChatMessageChunk(
  val id: String? = null,
  val created: Long,
  val content: String? = null,
  val stopReason: FinishReason? = null,
  val tokenUsage: TokenUsageAmount?,
  val toolCalls: List<ToolCallChunk>? = null,
)

/**
 * Data class representing incoming and outgoing chat messages.
 *
 * The `role` and `content` are always present.
 *
 * The `toolCalls` are present only if the LLM is configured to use function calling and is only
 * expected to be present on incoming messages.
 */
@Serializable
data class ChatMessage(
  val role: String,
  var content: String?,
  @SerialName("tool_calls") val toolCalls: List<ToolCallData>? = null,
  @SerialName("tool_call_id") val toolCallId: String? = null,

  /**
   * Here to capture the finish reason of the chat choice. Since we are calling completions
   * recursively due to tools, we have to capture the finish reason here.
   *
   * If this is present on the assistant message, the reason reflects why the LLM stopped
   * generating.
   */
  var finishReason: FinishReason? = null,
) {
  companion object {
    fun fromModel(model: Message): ChatMessage {
      return ChatMessage(model.senderType, model.content)
    }

    fun user(content: String): ChatMessage {
      return ChatMessage("user", content)
    }

    fun assistant(content: String?, toolCalls: List<ToolCallData>? = null): ChatMessage {
      return ChatMessage("assistant", content, toolCalls = toolCalls)
    }

    fun system(content: String): ChatMessage {
      return ChatMessage("system", content)
    }

    fun toolResult(content: String, toolCallId: String?): ChatMessage {
      return ChatMessage("tool", content, toolCallId = toolCallId)
    }
  }

  fun toInsert(finishReason: FinishReason? = null): MessageInsert {
    return MessageInsert(
      id = KUUID.randomUUID(),
      senderType = role,
      content = content,
      finishReason = finishReason,
      toolCalls = toolCalls,
      toolCallId = toolCallId,
    )
  }
}

@Serializable
data class ChatCompletion(
  val id: String,

  /** The creation time in epoch milliseconds. */
  val created: Long,

  /** The model used. */
  val model: String,

  /** A list of generated completions */
  val choices: List<ChatChoice>,

  /** The total amount of tokens spent. */
  val tokenUsage: TokenUsageAmount?,
)

@Serializable
data class ChatChoice(
  /** Chat choice index. */
  val index: Int,

  /** The generated chat message. */
  val message: ChatMessage,

  /** The reason why OpenAI stopped generating. */
  val finishReason: FinishReason?,
)

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
