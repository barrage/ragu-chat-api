package net.barrage.llmao.core.workflow

import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KUUID

/** Implement on classes that produce workflows. */
interface WorkflowFactory {
  /**
   * Used by workflow factories, and in turn the [WorkflowFactoryManager], to identify workflow
   * types.
   */
  fun id(): String

  suspend fun new(user: User, agentId: String?, emitter: Emitter): Workflow

  suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow
}

/**
 * Top level workflow factory registry.
 *
 * Used by the session manager to create workflows based on input messages.
 */
object WorkflowFactoryManager {
  private val factories: MutableMap<String, WorkflowFactory> = mutableMapOf()

  suspend fun new(workflowType: String, user: User, agentId: String?, emitter: Emitter): Workflow {
    if (!factories.containsKey(workflowType)) {
      throw AppError.api(ErrorReason.InvalidParameter, "Unsupported workflow type")
    }
    return factories[workflowType]!!.new(user, agentId, emitter)
  }

  suspend fun existing(
    workflowType: String,
    user: User,
    workflowId: KUUID,
    emitter: Emitter,
  ): Workflow {
    if (!factories.containsKey(workflowType)) {
      throw AppError.api(ErrorReason.InvalidParameter, "Unsupported workflow type")
    }
    return factories[workflowType]!!.existing(user, workflowId, emitter)
  }

  fun register(factory: WorkflowFactory) {
    factories[factory.id()] = factory
  }
}
