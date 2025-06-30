package net.barrage.llmao.core.workflow

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.types.KUUID

/** Standard input to all workflows. */
@Serializable
data class DefaultWorkflowInput(
  val text: String? = null,
  val attachments: List<IncomingMessageAttachment>? = null,
) {
  fun validate() {
    if (text.isNullOrBlank() && attachments.isNullOrEmpty()) {
      throw AppError.Companion.api(
        ErrorReason.InvalidParameter,
        "Workflow input must contain either text or attachments",
      )
    }
  }
}

/**
 * Marker class for all workflow outputs that should be sent to real time workflows via the session
 * manager.
 */
@Serializable
@JsonClassDiscriminator("type")
@OptIn(ExperimentalSerializationApi::class)
abstract class WorkflowOutput

/** Sent for each chunk the LLM outputs in streaming mode. */
@Serializable
@SerialName("workflow.stream_chunk")
data class StreamChunk(val chunk: String) : WorkflowOutput()

/** Sent when a chats gets a complete response from an LLM stream. */
@Serializable
@SerialName("workflow.stream_complete")
data class StreamComplete(
  /** The streaming chat ID */
  val workflowId: KUUID,

  /** What caused the stream to finish. */
  val reason: FinishReason,

  /**
   * The message group ID of the interaction. Present only when the stream finishes successfully.
   */
  val messageGroupId: KUUID? = null,

  /**
   * Contains a list of processed attachment paths, in the order they were sent by the client, if
   * any.
   */
  val attachmentPaths: List<MessageAttachment>? = null,

  /** If the response was not streamed, this will contain the output of the LLM. */
  val content: String? = null,
) : WorkflowOutput()
