package net.barrage.llmao.core.workflow

import io.ktor.util.logging.KtorSimpleLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.Event
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.Plugins
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.workflow.OutgoingSystemMessage.*

private val LOG = KtorSimpleLogger("n.b.l.c.workflow.SessionManager")

/**
 * The session manager creates sessions and is responsible for broadcasting system events to
 * clients.
 */
class SessionManager(private val plugins: Plugins, listener: EventListener) {
  /** Maps user ID + token pairs to their sessions. */
  private val workflows: MutableMap<Session, Workflow> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs directly to their output emitters. Used to broadcast system events
   * to all connected clients.
   */
  val systemSessions: MutableMap<Session, Emitter> = ConcurrentHashMap()

  val tokens: SessionTokenManager = SessionTokenManager()

  init {
    LOG.info("Starting session manager")
    listener.start(::handleEvent)
  }

  fun registerSystemEmitter(session: Session, emitter: Emitter) {
    systemSessions[session] = emitter
  }

  fun removeSystemEmitter(session: Session) {
    systemSessions.remove(session)
  }

  /** Removes the session and its corresponding workflow associated with the user and token pair. */
  fun removeWorkflow(session: Session) {
    LOG.info(
      "{} - removing workflow; total workflows in manager: {}",
      session.user.id,
      workflows.size,
    )
    workflows.remove(session)?.cancelStream()
  }

  fun retainWorkflows(condition: (Workflow) -> Boolean) =
    workflows.entries.retainAll { entry -> condition(entry.value) }

  suspend fun handleMessage(session: Session, message: String, emitter: Emitter) {
    try {
      val message = Json.decodeFromString<IncomingSystemMessage>(message)
      handleMessage(session, message, emitter)
    } catch (e: SerializationException) {
      throw AppError.api(ErrorReason.InvalidParameter, "Message format malformed", original = e)
    } catch (e: Throwable) {
      throw if (e is AppError) e else AppError.internal("Error in workflow", original = e)
    }
  }

  suspend inline fun <reified T> broadcast(value: T) {
    for (emitter in systemSessions.values) {
      emitter.emit(value, serializer<T>())
    }
  }

  /** Handle a system event from the [EventListener]. */
  private suspend fun handleEvent(event: Event) {
    plugins.emitEvent(this, event)
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

        systemSessions[session]?.emit(WorkflowOpen(workflow.id), OutgoingSystemMessage.serializer())

        LOG.debug(
          "{} - started workflow ({}); total workflows in manager: {}",
          session.user.id,
          message.workflowType,
          workflows.size,
        )
      }
      is IncomingSystemMessage.LoadExistingWorkflow -> {
        val workflow = workflows[session]

        // Prevent loading the same chat
        if (workflow != null && workflow.id == message.workflowId) {
          LOG.debug("{} - workflow already open ({})", session.user.id, workflow.id)
          systemSessions[session]?.emit(
            WorkflowOpen(workflow.id),
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
        systemSessions[session]?.emit(
          WorkflowOpen(message.workflowId),
          OutgoingSystemMessage.serializer(),
        )

        LOG.debug("{} - opened workflow {}", session.user.id, existingWorkflow.id)
      }
      is IncomingSystemMessage.CloseWorkflow -> {
        workflows.remove(session)?.let {
          systemSessions[session]?.emit(WorkflowClosed(it.id), OutgoingSystemMessage.serializer())
          it.cancelStream()
          LOG.debug(
            "{} - closed workflow; total workflows in manager: {}",
            session.user.id,
            workflows.size,
          )
        }
      }
      is IncomingSystemMessage.CancelWorkflowStream -> {
        workflows[session]?.cancelStream()
      }

      is IncomingSystemMessage.WorkflowInput ->
        try {
          val workflow =
            workflows[session]
              ?: throw AppError.api(
                ErrorReason.InvalidOperation,
                "Cannot accept input; no workflow open",
              )

          workflow.execute(message.input)
        } catch (e: SerializationException) {
          throw AppError.api(ErrorReason.InvalidParameter, "Message format malformed", original = e)
        } catch (e: Throwable) {
          throw if (e is AppError) e
          else AppError.internal("Unexpected error in workflow", original = e)
        }
    }
  }
}

/** A session represents a user's real time connection to the server. */
data class Session(val user: User, val token: String)
