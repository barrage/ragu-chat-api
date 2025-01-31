package net.barrage.llmao.core.workflow

import net.barrage.llmao.core.types.KUUID

/**
 * A workflow represents interactions between a user and an agent (or multiple).
 *
 * The simplest implementation of a workflow is a chat (see ChatWorkflow).
 */
interface Workflow {
  /** Get the workflow's ID (primary key). */
  fun id(): KUUID

  /** Get the workflow's agent ID. */
  fun entityId(): KUUID

  /**
   * The main entry point to the workflow.
   *
   * @param message The proompt.
   */
  fun execute(message: String)

  /** Check if the workflow is streaming. */
  fun isStreaming(): Boolean

  /** Cancel the current stream. */
  fun cancelStream()
}
