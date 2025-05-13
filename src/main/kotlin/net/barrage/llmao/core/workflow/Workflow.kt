package net.barrage.llmao.core.workflow

import net.barrage.llmao.types.KUUID

/** A workflow represents a real-time session between a user and an agent (or multiple). */
interface Workflow {
  /** The workflow's ID (primary key). */
  val id: KUUID

  /**
   * The main entry point to the workflow.
   *
   * @param input The input data for this workflow.
   */
  fun execute(input: String)

  /** Cancel the current stream. */
  fun cancelStream()
}
