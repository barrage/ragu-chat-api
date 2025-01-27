package net.barrage.llmao.core.session.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.types.KUUID

@Serializable
sealed class ChatSessionMessage {
  /** Sent when a chat's title is generated. */
  @Serializable
  @SerialName("chat_title")
  data class ChatTitleUpdated(val chatId: KUUID, val title: String) : ChatSessionMessage()

  @Serializable
  @SerialName("stream_chunk")
  data class StreamChunk(val chunk: String) : ChatSessionMessage()

  /** Sent when a chats gets a complete response from an LLM. */
  @Serializable
  @SerialName("finish_event")
  data class StreamComplete(
    /** The streaming chat ID */
    val chatId: KUUID,

    /** What caused the stream to finish. */
    val reason: FinishReason,

    /**
     * Optional message ID, present only when the finish reason is STOP. Failed message IDs are not
     * sent.
     */
    val messageId: KUUID? = null,
  ) : ChatSessionMessage()
}
