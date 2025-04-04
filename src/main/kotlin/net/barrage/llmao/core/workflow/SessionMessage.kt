package net.barrage.llmao.core.workflow

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.types.KUUID

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class SessionMessage {
  @Serializable
  @SerialName("system")
  data class Incoming(val payload: IncomingSystemMessage) : SessionMessage()

  data class Outgoing(val payload: OutgoingSystemMessage) : SessionMessage()
}

/** System messages used to control sessions. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed class IncomingSystemMessage {
  @Serializable
  @SerialName("workflow.new")
  data class CreateNewWorkflow(val agentId: KUUID? = null, val workflowType: String? = null) :
    IncomingSystemMessage()

  @Serializable
  @SerialName("workflow.existing")
  data class LoadExistingWorkflow(val workflowId: KUUID) : IncomingSystemMessage()

  @Serializable @SerialName("workflow.close") data object CloseWorkflow : IncomingSystemMessage()

  @Serializable
  @SerialName("workflow.cancel_stream")
  data object CancelWorkflowStream : IncomingSystemMessage()
}

/** Outgoing session messages. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
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
