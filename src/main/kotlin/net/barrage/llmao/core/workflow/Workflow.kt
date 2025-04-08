package net.barrage.llmao.core.workflow

import net.barrage.llmao.types.KUUID

/**
 * A workflow represents interactions between a user and an agent (or multiple).
 *
 * The simplest implementation of a workflow is a chat (see ChatWorkflow).
 */
interface Workflow {
  /** Get the workflow's ID (primary key). */
  fun id(): KUUID

  /** Get the workflow's agent ID. */
  fun agentId(): String

  /**
   * The main entry point to the workflow.
   *
   * @param input The input data for this workflow.
   */
  fun execute(input: String)

  /** Cancel the current stream. */
  fun cancelStream()
}
