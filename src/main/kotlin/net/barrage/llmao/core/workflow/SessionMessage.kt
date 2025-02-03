package net.barrage.llmao.core.workflow

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.chat.ChatWorkflowMessage

/** Incoming messages. */
@Serializable
sealed class IncomingMessage {
  @Serializable
  @SerialName("system")
  data class System(val payload: IncomingSystemMessage) : IncomingMessage()

  @Serializable @SerialName("chat") data class Chat(val text: String) : IncomingMessage()
}

/** System messages used to control sessions. */
@Serializable
sealed class IncomingSystemMessage {
  @Serializable
  @SerialName("workflow.new")
  data class CreateNewWorkflow(val agentId: KUUID) : IncomingSystemMessage()

  @Serializable
  @SerialName("workflow.existing")
  data class LoadExistingWorkflow(val workflowId: KUUID, val initialHistorySize: Int = 10) :
    IncomingSystemMessage()

  @Serializable @SerialName("workflow.close") data object CloseWorkflow : IncomingSystemMessage()

  @Serializable
  @SerialName("workflow.cancel_stream")
  data object CancelWorkflowStream : IncomingSystemMessage()
}

/** Outgoing session messages. */
@Serializable
sealed class OutgoingSystemMessage {
  /** Sent when a workflow is opened manually by the client. */
  @SerialName("system.workflow.open")
  @Serializable
  data class WorkflowOpen(val id: KUUID) : OutgoingSystemMessage()

  /** Sent when a workflow is closed manually by the client. */
  @SerialName("system.workflow.closed")
  @Serializable
  data class WorkflowClosed(val id: KUUID) : OutgoingSystemMessage()

  /**
   * Sent when an administrator deactivates an agent via the service and is used to indicate the
   * chat is no longer available.
   */
  @SerialName("system.event.agent_deactivated")
  @Serializable
  data class AgentDeactivated(val agentId: KUUID) : OutgoingSystemMessage()
}

val IncomingMessageSerializer = Json {
  // Register all the polymorphic subclasses
  classDiscriminator = "type" // Use "type" field for the discriminator
  serializersModule = SerializersModule {
    polymorphic(IncomingMessage::class) {
      subclass(IncomingMessage.System::class, IncomingMessage.System.serializer())
      subclass(IncomingMessage.Chat::class, IncomingMessage.Chat.serializer())
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
  }
}
