package net.barrage.llmao.app.api.ws

import io.ktor.server.websocket.*
import java.util.concurrent.ConcurrentHashMap
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.IncomingMessage
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.core.workflow.chat.ChatWorkflowMessage
import net.barrage.llmao.core.workflow.chat.LOG
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/**
 * The session manage creates sessions and is responsible for broadcasting system events to clients.
 */
class WebsocketSessionManager(
  private val factory: WorkflowFactory,
  listener: EventListener<StateChangeEvent>,
) {
  /** Maps user ID + token pairs to their sessions. */
  private val workflows: MutableMap<WebsocketSession, Workflow> = ConcurrentHashMap()

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
    message: IncomingMessage,
    ws: WebSocketServerSession,
  ) {
    when (message) {
      is IncomingMessage.Chat -> handleChatMessage(session, message.text)
      is IncomingMessage.System -> handleSystemMessage(session, message.payload, ws)
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

  private fun handleChatMessage(session: WebsocketSession, message: String) {
    val workflow =
      workflows[session]
        ?: throw AppError.api(
          ErrorReason.Websocket,
          "Chat not open for session '{}'".format(session),
        )

    if (workflow.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    LOG.debug("{} - sending input to workflow '{}'", session, workflow.id())

    workflow.send(message)
  }

  private suspend fun handleSystemMessage(
    session: WebsocketSession,
    message: IncomingSystemMessage,
    ws: WebSocketServerSession,
  ) {
    when (message) {
      is IncomingSystemMessage.CreateNewWorkflow -> {
        val emitter: Emitter<ChatWorkflowMessage> = WebsocketEmitter.new(ws)
        val toolEmitter: Emitter<ToolEvent> = WebsocketEmitter.new(ws)
        val workflow =
          factory.newChatWorkflow(
            userId = session.userId,
            agentId = message.agentId,
            emitter = emitter,
            toolEmitter = toolEmitter,
          )
        workflows[session] = workflow

        systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(workflow.id))

        LOG.debug(
          "{} - started workflow ({}) total workflows in manager: {}",
          session,
          workflow.id,
          workflows.size,
        )
      }
      is IncomingSystemMessage.LoadExistingWorkflow -> {
        val workflow = workflows[session]

        // Prevent loading the same chat
        if (workflow != null && workflow.id() == message.chatId) {
          LOG.debug("{} - workflow already open ({})", session, workflow.id())
          systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(workflow.id()))
          return
        }

        workflow?.cancelStream()

        val emitter: Emitter<ChatWorkflowMessage> = WebsocketEmitter.new(ws)
        val existingWorkflow =
          factory.fromExistingChatWorkflow(
            id = message.chatId,
            emitter = emitter,
            initialHistorySize = message.initialHistorySize,
          )

        workflows[session] = existingWorkflow
        systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowOpen(message.chatId))

        LOG.debug("{} - opened workflow {}", session, existingWorkflow.id)
      }
      is IncomingSystemMessage.CloseSession -> {
        workflows.remove(session)?.let {
          systemSessions[session]?.emit(OutgoingSystemMessage.WorkflowClosed(it.id()))
          it.cancelStream()
          LOG.debug(
            "{} - closed workflow ({}) total workflows in manager: {}",
            session,
            it.id(),
            workflows.size,
          )
        }
      }
      is IncomingSystemMessage.StopStream -> {
        workflows[session]?.let {
          LOG.debug("{} - cancelling stream in workflow '{}'", session, it.id())
          it.cancelStream()
        }
      }
    }
  }
}
