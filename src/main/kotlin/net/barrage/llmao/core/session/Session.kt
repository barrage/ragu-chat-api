package net.barrage.llmao.core.session

import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.types.KUUID

/**
 * A session represents interactions between a user and a communication entity. The entity can be an
 * agent or a workflow (a chain of agents). The simplest implementation of a session is a chat (see
 * ChatSession).
 */
interface Session {
  /** Get the session's ID (primary key). */
  fun id(): SessionId

  fun entityId(): SessionEntity

  /**
   * Execute the workflow.
   *
   * @param message The proompt.
   */
  fun startStream(message: String)

  /**
   * Check if the session is streaming.
   *
   * @return True if the session is streaming, false otherwise.
   */
  fun isStreaming(): Boolean

  /** Cancel the current stream. */
  fun cancelStream()
}

/** A stripped version of Agent down to the parameters required for a session. */
data class SessionAgent(
  val id: KUUID,
  val name: String,
  val model: String,
  val llmProvider: String,
  val context: String,
  val collections: List<SessionAgentCollection>,
  val instructions: AgentInstructions,
  val toolchain: Toolchain? = null,
  val temperature: Double,

  /** The agent configuration ID. */
  val configurationId: KUUID,
)

data class SessionAgentCollection(
  val name: String,
  val amount: Int,
  val instruction: String,
  val embeddingProvider: String,
  val embeddingModel: String,
  val vectorProvider: String,
)

/** A wrapper used to indicate the type of session. */
sealed class SessionId(open val id: KUUID) {
  data class Chat(override val id: KUUID) : SessionId(id)

  data class Workflow(override val id: KUUID) : SessionId(id)
}

/** The underlying entity of a session. */
sealed class SessionEntity(open val id: KUUID) {
  data class Agent(override val id: KUUID) : SessionEntity(id)

  data class Workflow(override val id: KUUID) : SessionEntity(id)
}
