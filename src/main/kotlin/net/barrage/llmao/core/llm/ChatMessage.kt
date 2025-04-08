package net.barrage.llmao.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.token.TokenUsageAmount
import net.barrage.llmao.types.KUUID

/**
 * Native chat message chunk (when streaming) used to handle potentially differing chunks from
 * downstream LLM providers.
 */
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
 *
 * TODO: Can this be a sealed class instead?
 */
@Serializable
data class ChatMessage(
  /** Can be one of: user, assistant, system, tool. */
  val role: String,

  /** Can be [ContentMulti] only on user messages. */
  var content: ChatMessageContent?,

  /**
   * Present only on assistant messages. Indicates which tool calls the assistant decided to use.
   * Usually, if present on the assistant message, the content is null.
   */
  @SerialName("tool_calls") val toolCalls: List<ToolCallData>? = null,

  /** Present only on tool messages. Indicates which tool call this message is a response to. */
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
  fun toInsert(attachments: List<MessageAttachment>? = null): MessageInsert {
    return MessageInsert(
      id = KUUID.randomUUID(),
      senderType = role,
      content = content?.text(),
      finishReason = this.finishReason ?: finishReason,
      toolCalls = toolCalls,
      toolCallId = toolCallId,
      attachments = attachments,
    )
  }

  companion object {
    /** Create a user message with the given content. */
    fun user(content: ChatMessageContent): ChatMessage {
      return ChatMessage("user", content)
    }

    /** Create a user message with [ContentSingle] as its content. */
    fun user(text: String): ChatMessage {
      return ChatMessage("user", ContentSingle(text))
    }

    /**
     * Create a chat message with `assistant` role. All assistant messages have [ContentSingle] as
     * their content.
     */
    fun assistant(
      content: String?,
      toolCalls: List<ToolCallData>? = null,
      finishReason: FinishReason,
    ): ChatMessage {
      return ChatMessage(
        "assistant",
        content?.let(::ContentSingle),
        toolCalls = toolCalls,
        finishReason = finishReason,
      )
    }

    /**
     * Create a chat message with `system` role. All system messages have [ContentSingle] as their
     * content.
     */
    fun system(content: String): ChatMessage {
      return ChatMessage("system", ContentSingle(content))
    }

    /**
     * Create a chat message with `tool` role. All tool messages have [ContentSingle] as their
     * content.
     */
    fun toolResult(content: String, toolCallId: String?): ChatMessage {
      return ChatMessage("tool", ContentSingle(content), toolCallId = toolCallId)
    }
  }
}

/**
 * Represents message content.
 *
 * The only time this can be [ContentMulti] is in user messages with attachments.
 *
 * Every instance of [ContentMulti] is *guaranteed* to have one [ChatMessageContentPart.Text] at the
 * beginning of the list.
 *
 * *All* messages of `role != user` are guaranteed to have [ContentSingle] as their content.
 */
@Serializable
sealed class ChatMessageContent {
  /**
   * Get the text contents of this message.
   *
   * In cases of [ContentMulti], this will return the first [ChatMessageContentPart.Text].
   *
   * Since only user messages can be [ContentMulti], they are guaranteed to have a text part.
   */
  fun text(): String {
    return when (this) {
      is ContentSingle -> this.content
      is ContentMulti -> {
        val text = this.content.first { it is ChatMessageContentPart.Text }
        (text as ChatMessageContentPart.Text).text
      }
    }
  }

  /** Create a copy of this content with its text replaced with [text]. */
  fun copyWithText(text: String): ChatMessageContent {
    return when (this) {
      is ContentSingle -> ContentSingle(text)
      is ContentMulti ->
        ContentMulti(
          content.map {
            if (it is ChatMessageContentPart.Text) ChatMessageContentPart.Text(text) else it
          }
        )
    }
  }
}

/** A single text message (string) is being sent to the model. */
@Serializable data class ContentSingle(val content: String) : ChatMessageContent()

/** Message content with attachments. */
@Serializable
data class ContentMulti(val content: List<ChatMessageContentPart>) : ChatMessageContent() {
  init {
    assert(content.first() is ChatMessageContentPart.Text) { "First content part must be text" }
  }
}

/** Possible enumeration of message content parts. */
@Serializable
sealed class ChatMessageContentPart(val type: String) {
  /** Represents a textual prompt for the LLM. */
  @Serializable
  data class Text(@SerialName("text") val text: String) : ChatMessageContentPart("input_text")

  /** Represents an image attachment for the LLM. */
  @Serializable
  data class Image(@SerialName("image_url") val imageUrl: ChatMessageImage) :
    ChatMessageContentPart("input_image")
}

@Serializable data class ChatMessageImage(val url: String, val detail: String? = null)

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
