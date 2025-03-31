package net.barrage.llmao.app.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.barrage.llmao.core.chat.ChatWorkflowMessage
import net.barrage.llmao.app.specialist.jirakira.JiraKiraMessage
import net.barrage.llmao.core.model.IncomingImageData
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage

/**
 * Enumeration of all incoming messages in the application layer. Having a sealed class like this
 * allows us to deserialize messages and setup their handlers more easily.
 */
@Serializable
sealed class IncomingSessionMessage {
  @Serializable
  @SerialName("system")
  data class System(val payload: IncomingSystemMessage) : IncomingSessionMessage()

  @Serializable
  @SerialName("chat")
  data class Chat(val text: String, val attachments: List<IncomingMessageAttachment>? = null) :
    IncomingSessionMessage()
}

val IncomingSessionMessageSerializer = Json {
  // Register all the polymorphic subclasses
  ignoreUnknownKeys = true
  classDiscriminator = "type" // Use "type" field for the discriminator
  serializersModule = SerializersModule {
    polymorphic(IncomingSessionMessage::class) {
      subclass(IncomingSessionMessage.System::class, IncomingSessionMessage.System.serializer())
      subclass(IncomingSessionMessage.Chat::class, IncomingSessionMessage.Chat.serializer())
    }
    polymorphic(IncomingSystemMessage::class) {
      subclass(
        IncomingSystemMessage.CreateNewWorkflow::class,
        IncomingSystemMessage.CreateNewWorkflow.serializer(),
      )
      subclass(
        IncomingSystemMessage.LoadExistingWorkflow::class,
        IncomingSystemMessage.LoadExistingWorkflow.serializer(),
      )
      subclass(
        IncomingSystemMessage.CloseWorkflow::class,
        IncomingSystemMessage.CloseWorkflow.serializer(),
      )
      subclass(
        IncomingSystemMessage.CancelWorkflowStream::class,
        IncomingSystemMessage.CancelWorkflowStream.serializer(),
      )
    }
    polymorphic(OutgoingSystemMessage::class) {
      subclass(
        OutgoingSystemMessage.WorkflowOpen::class,
        OutgoingSystemMessage.WorkflowOpen.serializer(),
      )
      subclass(
        OutgoingSystemMessage.WorkflowClosed::class,
        OutgoingSystemMessage.WorkflowClosed.serializer(),
      )
      subclass(
        OutgoingSystemMessage.AgentDeactivated::class,
        OutgoingSystemMessage.AgentDeactivated.serializer(),
      )
    }
    polymorphic(ChatWorkflowMessage::class) {
      subclass(ChatWorkflowMessage.StreamChunk::class, ChatWorkflowMessage.StreamChunk.serializer())
      subclass(
        ChatWorkflowMessage.StreamComplete::class,
        ChatWorkflowMessage.StreamComplete.serializer(),
      )
      subclass(
        ChatWorkflowMessage.ChatTitleUpdated::class,
        ChatWorkflowMessage.ChatTitleUpdated.serializer(),
      )
    }
    polymorphic(JiraKiraMessage::class) {
      subclass(JiraKiraMessage.LlmResponse::class, JiraKiraMessage.LlmResponse.serializer())
    }
    polymorphic(IncomingMessageAttachment::class) {
      subclass(IncomingMessageAttachment.Image::class, IncomingMessageAttachment.Image.serializer())
    }
    polymorphic(IncomingImageData::class) {
      subclass(IncomingImageData.Url::class, IncomingImageData.Url.serializer())
      subclass(IncomingImageData.Raw::class, IncomingImageData.Raw.serializer())
    }
  }
}
