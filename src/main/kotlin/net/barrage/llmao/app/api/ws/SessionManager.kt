package net.barrage.llmao.app.api.ws

import io.ktor.util.logging.KtorSimpleLogger
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.AdapterState
import net.barrage.llmao.app.specialist.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.app.workflow.WorkflowType
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.chat.WorkflowFactory
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.core.workflow.Workflow

private val LOG = KtorSimpleLogger("net.barrage.llmao.app.api.ws.WebsocketSessionManager")

/**
 * The session manage creates sessions and is responsible for broadcasting system events to clients.
 */
class SessionManager(
  private val factory: WorkflowFactory,
  private val adapters: AdapterState,
  listener: EventListener<StateChangeEvent>,
) {
  /** Maps user ID + token pairs to their sessions. */
  private val workflows: MutableMap<Session, Workflow> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs directly to their output emitters. Used to broadcast system events
   * to all connected clients.
   */
  private val systemSessions: MutableMap<Session, Emitter> = ConcurrentHashMap()

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

  /** Removes the session and its corresponding chat associated with the user and token pair. */
  fun removeWorkflow(session: Session) {
    LOG.info("{} - removing session", session.user.id)
    workflows.remove(session)
  }

  suspend fun handleMessage(session: Session, message: String, emitter: Emitter) {
    try {
      val message = Json.decodeFromString<IncomingSystemMessage>(message)
      handleSystemMessage(session, message, emitter)
    } catch (e: SerializationException) {
      LOG.debug("Forwarding message to workflow ({})", e.message)
      try {
        handleChatMessage(session, message)
      } catch (e: SerializationException) {
        throw AppError.api(ErrorReason.InvalidParameter, "Message format malformed", original = e)
      } catch (e: Throwable) {
        throw if (e is AppError) {
          e
        } else AppError.internal("Error in workflow", original = e)
      }
    } catch (e: Throwable) {
      if (e is AppError) {
        throw e
      }
      throw AppError.internal("Error in workflow", original = e)
    }
  }

  /** Handle a system event from the [EventListener]. */
  private suspend fun handleEvent(event: StateChangeEvent) {
    when (event) {
      is StateChangeEvent.AgentDeactivated -> {
        LOG.info("Handling agent deactivated event ({})", event.agentId)

        workflows.values.retainAll { chat -> chat.agentId() != event.agentId.toString() }

        for (channel in systemSessions.values) {
          channel.emit(
            OutgoingSystemMessage.AgentDeactivated(event.agentId),
            OutgoingSystemMessage::class,
          )
        }
      }
    }
  }

  private fun handleChatMessage(session: Session, input: String) {
    val workflow =
      workflows[session] ?: throw AppError.api(ErrorReason.Websocket, "Workflow not opened")

    LOG.debug("{} - sending input to workflow '{}'", session.user.id, workflow.id())

    workflow.execute(input)
  }

  private suspend fun handleSystemMessage(
    session: Session,
    message: IncomingSystemMessage,
    emitter: Emitter,
  ) {
    when (message) {
      is IncomingSystemMessage.CreateNewWorkflow -> {
        val id =
          when (message.workflowType) {
            null,
            WorkflowType.CHAT.name -> {
              LOG.debug("{} - opening chat workflow", session.user.id)
              val workflow =
                factory.newChatWorkflow(
                  user = session.user,
                  agentId =
                    message.agentId
                      ?: throw AppError.api(ErrorReason.InvalidParameter, "Missing agentId"),
                  emitter = emitter,
                )
              workflows[session] = workflow

              systemSessions[session]?.emit(
                OutgoingSystemMessage.WorkflowOpen(workflow.id),
                OutgoingSystemMessage::class,
              )

              workflow.id
            }
            WorkflowType.JIRAKIRA.name -> {
              val jkFactory =
                adapters.adapterForFeature<JiraKiraWorkflowFactory>()
                  ?: throw AppError.api(ErrorReason.InvalidParameter, "Unsupported workflow type")

              LOG.debug("{} - opening JiraKira workflow", session.user.id)

              val workflow = jkFactory.newJiraKiraWorkflow(user = session.user, emitter = emitter)

              workflows[session] = workflow

              systemSessions[session]?.emit(
                OutgoingSystemMessage.WorkflowOpen(workflow.id),
                OutgoingSystemMessage::class,
              )

              workflow.id
            }
            else -> throw AppError.api(ErrorReason.InvalidParameter, "Unsupported workflow type")
          }

        LOG.debug(
          "{} - started workflow ({}) total workflows in manager: {}",
          session.user.id,
          id,
          workflows.size,
        )
      }
      is IncomingSystemMessage.LoadExistingWorkflow -> {
        val workflow = workflows[session]

        // Prevent loading the same chat
        if (workflow != null && workflow.id() == message.workflowId) {
          LOG.debug("{} - workflow already open ({})", session.user.id, workflow.id())
          systemSessions[session]?.emit(
            OutgoingSystemMessage.WorkflowOpen(workflow.id()),
            OutgoingSystemMessage::class,
          )
          return
        }

        workflow?.cancelStream()

        val existingWorkflow =
          factory.existingChatWorkflow(
            id = message.workflowId,
            user = session.user,
            emitter = emitter,
          )

        workflows[session] = existingWorkflow
        systemSessions[session]?.emit(
          OutgoingSystemMessage.WorkflowOpen(message.workflowId),
          OutgoingSystemMessage::class,
        )

        LOG.debug("{} - opened workflow {}", session.user.id, existingWorkflow.id)
      }
      is IncomingSystemMessage.CloseWorkflow -> {
        workflows.remove(session)?.let {
          systemSessions[session]?.emit(
            OutgoingSystemMessage.WorkflowClosed(it.id()),
            OutgoingSystemMessage::class,
          )
          it.cancelStream()
          LOG.debug(
            "{} - closed workflow ({}) total workflows in manager: {}",
            session.user.id,
            it.id(),
            workflows.size,
          )
        }
      }
      is IncomingSystemMessage.CancelWorkflowStream -> {
        workflows[session]?.let {
          LOG.debug("{} - cancelling stream in workflow '{}'", session.user.id, it.id())
          it.cancelStream()
        }
      }
    }
  }
}

/** A session represents a user's connection to the server. */
data class Session(val user: User, val token: KUUID)
