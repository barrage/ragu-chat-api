package net.barrage.llmao.core.workflow

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.types.KUUID

/** System messages used to control sessions. */
@Serializable
@JsonClassDiscriminator("type")
@OptIn(ExperimentalSerializationApi::class)
sealed class IncomingSystemMessage {
  @Serializable
  @SerialName("workflow.new")
  data class CreateNewWorkflow(val workflowType: String, val params: JsonElement?) :
    IncomingSystemMessage()

  @Serializable
  @SerialName("workflow.existing")
  data class LoadExistingWorkflow(val workflowType: String, val workflowId: KUUID) :
    IncomingSystemMessage()

  @Serializable @SerialName("workflow.close") data object CloseWorkflow : IncomingSystemMessage()

  @Serializable
  @SerialName("workflow.cancel_stream")
  data object CancelWorkflowStream : IncomingSystemMessage()
}

/** Outgoing session messages. */
@Serializable
@JsonClassDiscriminator("type")
@OptIn(ExperimentalSerializationApi::class)
sealed class OutgoingSystemMessage {
  /** Sent when a workflow is opened manually by the client. */
  @SerialName("workflow.open")
  @Serializable
  data class WorkflowOpen(val id: KUUID) : OutgoingSystemMessage()

  /** Sent when a workflow is closed manually by the client. */
  @SerialName("workflow.closed")
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
