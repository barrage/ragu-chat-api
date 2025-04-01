package net.barrage.llmao.app.api.ws

import io.ktor.server.websocket.WebSocketServerSession
import io.ktor.util.logging.KtorSimpleLogger
import java.util.concurrent.ConcurrentHashMap
import net.barrage.llmao.app.AdapterState
import net.barrage.llmao.app.specialist.jirakira.JiraKiraWorkflow
import net.barrage.llmao.app.specialist.jirakira.JiraKiraWorkflowFactory
import net.barrage.llmao.app.workflow.IncomingSessionMessage
import net.barrage.llmao.app.workflow.WorkflowType
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.chat.ChatWorkflow
import net.barrage.llmao.core.chat.ChatWorkflowFactory
import net.barrage.llmao.core.chat.ChatWorkflowInput
import net.barrage.llmao.core.chat.ChatWorkflowMessage
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.model.IncomingMessageAttachment
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
class WebsocketSessionManager(
  private val factory: ChatWorkflowFactory,
  private val adapters: AdapterState,
  listener: EventListener<StateChangeEvent>,
) {
  /** Maps user ID + token pairs to their sessions. */
  private val workflows: MutableMap<WebsocketSession, Workflow<*>> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs directly to their output emitters. Used to broadcast system events
   * to all connected clients.
   */
  private val systemSessions: MutableMap<WebsocketSession, Emitter<OutgoingSystemMessage>> =
    ConcurrentHashMap()

  init {
    LOG.info("Starting WS server event listener")
    listener.start { event -> handleEvent(event) }
  }

  fun registerSystemEmitter(session: WebsocketSession, emitter: Emitter<OutgoingSystemMessage>) {
    systemSessions[session] = emitter
  }

  fun removeSystemEmitter(session: WebsocketSession) {
    systemSessions.remove(session)
  }

  /** Removes the session and its corresponding chat associated with the user and token pair. */
  fun removeWorkflow(session: WebsocketSession) {
    LOG.info("Removing session {}", session)
    workflows.remove(session)
  }

  suspend fun handleMessage(
    session: WebsocketSession,
    message: IncomingSessionMessage,
    ws: WebSocketServerSession,
  ) {
    when (message) {
      is IncomingSessionMessage.Chat ->
        handleChatMessage(session, message.text, message.attachments)
      is IncomingSessionMessage.System -> handleSystemMessage(session, message.payload, ws)
    }
  }

  /** Handle a system event from the [EventListener]. */
  private suspend fun handleEvent(event: StateChangeEvent) {
    when (event) {
      is StateChangeEvent.AgentDeactivated -> {
        LOG.info("Handling agent deactivated event ({})", event.agentId)

        workflows.values.retainAll { chat -> chat.entityId() != event.agentId }

        for (channel in systemSessions.values) {
          channel.emit(OutgoingSystemMessage.AgentDeactivated(event.agentId))
        }
      }
    }
  }

  private fun handleChatMessage(
    session: WebsocketSession,
    message: String,
    attachments: List<IncomingMessageAttachment>?,
  ) {
    val workflow =
      workflows[session] ?: throw AppError.api(ErrorReason.Websocket, "Workflow not opened")

    LOG.debug("{} - sending input to workflow '{}'", session.user.id, workflow.id())

    when (workflow) {
      is ChatWorkflow -> {
        workflow.execute(ChatWorkflowInput(message, attachments))
      }
      is JiraKiraWorkflow -> {
        workflow.execute(message)
      }
      else -> throw AppError.api(ErrorReason.Websocket, "Workflow does not support message type")
    }
  }

  private suspend fun handleSystemMessage(
    session: WebsocketSession,
    message: IncomingSystemMessage,
    ws: WebSocketServerSession,
  ) {
    when (message) {
      is IncomingSystemMessage.CreateNewWorkflow -> {
        val id =
          when (message.workflowType) {
            null,
            WorkflowType.CHAT.name -> {
              LOG.debug("{} - opening chat workflow", session.user.id)
              val emitter: Emitter<ChatWorkflowMessage> = WebsocketEmitter.new(ws)
              val toolEmitter: Emitter<ToolEvent> = WebsocketEmitter.new(ws)
              val workflow =
                factory.newChatWorkflow(
                  user = session.user,
                  agentId =
                    message.agentId
                      ?: throw AppError.api(ErrorReason.InvalidParameter, "Missing agentId"),
                  emitter = emitter,
                  toolEmitter = toolEmitter,
                )
              workflows[session] = workflow

              systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(workflow.id))

              workflow.id
            }
            WorkflowType.JIRAKIRA.name -> {
              val jkFactory =
                adapters.adapterForFeature<JiraKiraWorkflowFactory>()
                  ?: throw AppError.api(ErrorReason.InvalidParameter, "Unsupported workflow type")

              LOG.debug("{} - opening JiraKira workflow", session.user.id)

              val workflow =
                jkFactory.newJiraKiraWorkflow(
                  user = session.user,
                  emitter = WebsocketEmitter.new(ws),
                  toolEmitter = WebsocketEmitter.new(ws),
                )

              workflows[session] = workflow

              systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(workflow.id))

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
          systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(workflow.id()))
          return
        }

        workflow?.cancelStream()

        val emitter: Emitter<ChatWorkflowMessage> = WebsocketEmitter.new(ws)
        val existingWorkflow =
          factory.fromExistingChatWorkflow(
            id = message.workflowId,
            user = session.user,
            emitter = emitter,
          )

        workflows[session] = existingWorkflow
        systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(message.workflowId))

        LOG.debug("{} - opened workflow {}", session.user.id, existingWorkflow.id)
      }
      is IncomingSystemMessage.CloseWorkflow -> {
        workflows.remove(session)?.let {
          systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowClosed(it.id()))
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

data class WebsocketSession(val user: User, val token: KUUID)
