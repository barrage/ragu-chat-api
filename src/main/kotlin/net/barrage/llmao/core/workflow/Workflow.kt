package net.barrage.llmao.core.workflow

import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.types.KUUID

/**
 * A workflow represents interactions between a user and an agent.
 *
 * The simplest implementation of a workflow is a chat (see ChatWorkflow).
 */
interface Workflow {
  /** Get the workflow's ID (primary key). */
  fun id(): KUUID

  /** Get the workflow's agent ID. */
  fun entityId(): KUUID

  /**
   * Send a message to the workflow.
   *
   * @param message The proompt.
   */
  fun send(message: String)

  /** Check if the workflow is streaming. */
  fun isStreaming(): Boolean

  /** Cancel the current stream. */
  fun cancelStream()
}

/** A stripped version of Agent down to the parameters required for a workflow. */
data class WorkflowAgent(
  val id: KUUID,
  val name: String,
  val model: String,
  val llmProvider: String,
  val context: String,
  val collections: List<WorkflowAgentCollection>,
  val instructions: AgentInstructions,
  var tools: List<ToolDefinition>? = null,
  val temperature: Double,

  /** The agent configuration ID. */
  val configurationId: KUUID,
)

data class WorkflowAgentCollection(
  val name: String,
  val amount: Int,
  val instruction: String,
  val embeddingProvider: String,
  val embeddingModel: String,
  val vectorProvider: String,
)
