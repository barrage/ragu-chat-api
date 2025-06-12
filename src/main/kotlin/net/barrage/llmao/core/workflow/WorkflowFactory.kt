package net.barrage.llmao.core.workflow

import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.Identity
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID

/**
 * Implement on classes that produce workflows.
 *
 * [Identity] is used by workflow factories, and in turn the [WorkflowFactoryManager], to identify
 * workflow types.
 */
interface WorkflowFactory : Identity {
  /** Instantiate a new workflow. */
  suspend fun new(user: User, emitter: Emitter, params: JsonElement?): Workflow

  /** Continue an existing workflow. */
  suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow
}

/**
 * Top level workflow factory registry.
 *
 * Used by the session manager to create workflows based on input messages.
 */
object WorkflowFactoryManager {
  private val factories: MutableMap<String, WorkflowFactory> = mutableMapOf()

  suspend fun new(
    workflowType: String,
    user: User,
    emitter: Emitter,
    params: JsonElement?,
  ): Workflow {
    val factory =
      factories[workflowType]
        ?: throw AppError.api(ErrorReason.InvalidParameter, "Unsupported workflow type")
    return factory.new(user, emitter, params)
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
