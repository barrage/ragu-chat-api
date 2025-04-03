package net.barrage.llmao.core.chat

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.types.KUUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class ChatWorkflowMessage {
  /** Sent when a chat's title is generated. */
  @Serializable
  @SerialName("chat.title")
  data class ChatTitleUpdated(val chatId: KUUID, val title: String) : ChatWorkflowMessage()

  @Serializable
  @SerialName("chat.stream_chunk")
  data class StreamChunk(val chunk: String) : ChatWorkflowMessage()

  /** Sent when a chats gets a complete response from an LLM. */
  @Serializable
  @SerialName("chat.stream_complete")
  data class StreamComplete(
    /** The streaming chat ID */
    val chatId: KUUID,

    /** What caused the stream to finish. */
    val reason: FinishReason,

    /**
     * The message group ID of the interaction. Present only when the stream finishes successfully.
     */
    val messageId: KUUID? = null,

    /**
     * Contains a list of processed attachment paths, in the order they were sent by the client, if
     * any.
     */
    val attachmentPaths: List<MessageAttachment>? = null,
  ) : ChatWorkflowMessage()
}
