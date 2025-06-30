package net.barrage.llmao.core.workflow

import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonClassDiscriminator
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.logger
import net.barrage.llmao.core.model.User

/**
 * The session manager creates sessions and is responsible for broadcasting system events to
 * clients.
 *
 * Only one instance of this class is created per application and is responsible for managing all
 * real time workflows.
 */
class SessionManager {
  private val log = logger(SessionManager::class)

  /** Maps user ID + token pairs to their sessions. */
  private val workflows: MutableMap<Session, Workflow> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs directly to their output emitters. Used to broadcast system events
   * to all connected clients.
   */
  private val emitters: MutableMap<Session, Emitter> = ConcurrentHashMap()

  val tokens: SessionTokenManager = SessionTokenManager()

  companion object {
    private var instance: SessionManager? = null

    /**
     * Broadcast an event to all connected clients. This static method uses the singleton instance's
     * emitters to broadcast the event.
     */
    suspend fun broadcastEvent(event: Event) {
      instance?.broadcast(event) ?: throw IllegalStateException("SessionManager not initialized")
    }
  }

  init {
    log.info("Starting session manager")
    instance = this
  }

  fun registerSystemEmitter(session: Session, emitter: Emitter) {
    emitters[session] = emitter
    log.info("{} - registered system emitter; total: {}", session.user.id, emitters.size)
  }

  fun removeSystemEmitter(session: Session) {
    emitters.remove(session)
    log.info("{} - removed system emitter; total: {}", session.user.id, emitters.size)
  }

  /** Removes the session and its corresponding workflow associated with the user and token pair. */
  fun removeWorkflow(session: Session) {
    workflows.remove(session)?.cancelStream()
    log.info("{} - removed workflow; total: {}", session.user.id, workflows.size)
  }

  suspend fun handleMessage(session: Session, message: String, emitter: Emitter) {
    try {
      val message = Json.decodeFromString<IncomingSystemMessage>(message)
      handleMessage(session, message, emitter)
    } catch (e: SerializationException) {
      throw AppError.Companion.api(
        ErrorReason.InvalidParameter,
        "Message format malformed",
        original = e,
      )
    } catch (e: Throwable) {
      throw e as? AppError ?: AppError.Companion.internal("Error in workflow", original = e)
    }
  }

  suspend fun broadcast(value: Event) {
    log.debug("Broadcasting event to {} total clients: {}", emitters.size, value)
    for (emitter in emitters.values) {
      emitter.emit(value)
    }
  }

  private suspend fun handleMessage(
    session: Session,
    message: IncomingSystemMessage,
    emitter: Emitter,
  ) {
    when (message) {
      is IncomingSystemMessage.CreateNewWorkflow -> {
        val workflowType = message.workflowType
        val params = message.params

        val workflow =
          WorkflowFactoryManager.new(
            workflowType = workflowType,
            user = session.user,
            emitter = emitter,
            params = params,
          )

        workflows[session] = workflow

        emitters[session]?.emit(
          OutgoingSystemMessage.WorkflowOpen(workflow.id),
          OutgoingSystemMessage.serializer(),
        )

        log.debug(
          "{} - started workflow ({}); total: {}",
          session.user.id,
          message.workflowType,
          workflows.size,
        )
      }

      is IncomingSystemMessage.LoadExistingWorkflow -> {
        val workflow = workflows[session]

        // Prevent loading the same chat
        if (workflow != null && workflow.id == message.workflowId) {
          log.debug("{} - workflow already open ({})", session.user.id, workflow.id)
          emitters[session]?.emit(
            OutgoingSystemMessage.WorkflowOpen(workflow.id),
            OutgoingSystemMessage.serializer(),
          )
          return
        }

        workflow?.cancelStream()

        val existingWorkflow =
          WorkflowFactoryManager.existing(
            workflowType = message.workflowType,
            workflowId = message.workflowId,
            user = session.user,
            emitter = emitter,
          )

        workflows[session] = existingWorkflow
        emitters[session]?.emit(
          OutgoingSystemMessage.WorkflowOpen(message.workflowId),
          OutgoingSystemMessage.serializer(),
        )

        log.debug("{} - opened workflow {}", session.user.id, existingWorkflow.id)
      }

      is IncomingSystemMessage.CloseWorkflow -> {
        workflows.remove(session)?.let {
          emitters[session]?.emit(
            OutgoingSystemMessage.WorkflowClosed(it.id),
            OutgoingSystemMessage.serializer(),
          )
          it.cancelStream()
          log.debug("{} - closed workflow; total: {}", session.user.id, workflows.size)
        }
      }

      is IncomingSystemMessage.CancelWorkflowStream -> {
        workflows[session]?.cancelStream()
      }

      is IncomingSystemMessage.WorkflowInput ->
        try {
          val workflow =
            workflows[session]
              ?: throw AppError.Companion.api(
                ErrorReason.InvalidOperation,
                "Cannot accept input; no workflow open",
              )

          workflow.execute(message.input)
        } catch (e: SerializationException) {
          throw AppError.Companion.api(
            ErrorReason.InvalidParameter,
            "Message format malformed",
            original = e,
          )
        } catch (e: Throwable) {
          throw e as? AppError
            ?: AppError.Companion.internal("Unexpected error in workflow", original = e)
        }
    }
  }
}

/** A session represents a user's real time connection to the server. */
data class Session(val user: User, val token: String)

/** Marker class for events that should be broadcast to real-time workflows. */
@Serializable
@OptIn(ExperimentalSerializationApi::class)
@JsonClassDiscriminator("type")
abstract class Event
