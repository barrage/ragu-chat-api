package net.barrage.llmao.app.api.ws

import io.ktor.server.websocket.*
import java.util.concurrent.ConcurrentHashMap
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.types.KUUID
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
  private val workflowSessions: MutableMap<Pair<KUUID, KUUID>, Workflow> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs directly to their output emitters. Used to broadcast system events
   * to all connected clients.
   */
  private val systemSessions: MutableMap<Pair<KUUID, KUUID>, Emitter<OutgoingSystemMessage>> =
    ConcurrentHashMap()

  init {
    LOG.info("Starting WS server event listener")
    listener.start { event -> handleEvent(event) }
  }

  fun registerSystemSession(userId: KUUID, token: KUUID, emitter: Emitter<OutgoingSystemMessage>) {
    systemSessions[key(userId, token)] = emitter
  }

  /** Removes the session and its corresponding chat associated with the user and token pair. */
  fun removeAllSessions(userId: KUUID, token: KUUID) {
    LOG.info("Removing session for '{}' with token '{}", userId, token)
    val channel = systemSessions.remove(key(userId, token))
    workflowSessions.remove(key(userId, token))

    // Has to be closed manually to stop the job from running
    channel?.close()
  }

  suspend fun handleMessage(
    userId: KUUID,
    token: KUUID,
    message: IncomingMessage,
    ws: WebSocketServerSession,
  ) {
    when (message) {
      is IncomingMessage.Chat -> handleChatMessage(userId, token, message.text)
      is IncomingMessage.System -> handleSystemMessage(userId, token, message.payload, ws)
    }
  }

  /** Handle a system event from the [EventListener]. */
  private suspend fun handleEvent(event: StateChangeEvent) {
    when (event) {
      is StateChangeEvent.AgentDeactivated -> {
        LOG.info("Handling agent deactivated event ({})", event.agentId)

        workflowSessions.values.retainAll { chat -> chat.entityId() != event.agentId }

        for (channel in systemSessions.values) {
          channel.emit(OutgoingSystemMessage.AgentDeactivated(event.agentId))
        }
      }
    }
  }

  private fun handleChatMessage(userId: KUUID, token: KUUID, message: String) {
    LOG.info("Handling chat message from '{}' with token '{}': {}", userId, token, message)

    val chat =
      workflowSessions[key(userId, token)]
        ?: throw AppError.api(
          ErrorReason.Websocket,
          "Chat not open for user '$userId' with token '$token'",
        )

    if (chat.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    LOG.debug("Starting stream in '{}' for user '{}' with token '{}'", chat.id(), userId, token)

    chat.send(message)
  }

  private suspend fun handleSystemMessage(
    userId: KUUID,
    token: KUUID,
    message: IncomingSystemMessage,
    ws: WebSocketServerSession,
  ) {
    when (message) {
      is IncomingSystemMessage.CreateNewSession -> {
        val emitter: Emitter<ChatWorkflowMessage> = WebsocketEmitter.new(ws)
        val toolEmitter: Emitter<ToolEvent> = WebsocketEmitter.new(ws)
        val chat =
          factory.newChatWorkflow(
            userId = userId,
            agentId = message.agentId,
            emitter = emitter,
            toolEmitter = toolEmitter,
          )
        workflowSessions[key(userId, token)] = chat

        systemSessions[key(userId, token)]?.emit(OutgoingSystemMessage.SessionOpen(chat.id))

        LOG.debug(
          "Opened new chat ('{}') for '{}' with token '{}', total chats: {}",
          chat.id,
          userId,
          token,
          workflowSessions.size,
        )
      }
      is IncomingSystemMessage.LoadExistingSession -> {
        val session = workflowSessions[key(userId, token)]

        // Prevent loading the same chat
        if (session != null && session.id() == message.chatId) {
          LOG.debug("Existing chat has same ID as opened chat '{}'", session.id())
          systemSessions[key(userId, token)]?.emit(OutgoingSystemMessage.SessionOpen(session.id()))
          return
        }

        session?.cancelStream()

        val emitter: Emitter<ChatWorkflowMessage> = WebsocketEmitter.new(ws)
        val existingChat =
          factory.fromExistingChatWorkflow(
            id = message.chatId,
            emitter = emitter,
            initialHistorySize = message.initialHistorySize,
          )

        workflowSessions[key(userId, token)] = existingChat
        systemSessions[key(userId, token)]?.emit(OutgoingSystemMessage.SessionOpen(message.chatId))

        LOG.debug(
          "Opened existing chat ('{}') for '{}' with token '{}'",
          existingChat.id,
          userId,
          token,
        )
      }
      is IncomingSystemMessage.CloseSession -> {
        workflowSessions.remove(key(userId, token))?.let {
          systemSessions[key(userId, token)]?.emit(OutgoingSystemMessage.SessionClosed(it.id()))
          it.cancelStream()
          LOG.debug(
            "Closed chat ('{}') for user '{}' with token '{}', total chats: {}",
            it.id(),
            userId,
            token,
            workflowSessions.size,
          )
        }
      }
      is IncomingSystemMessage.StopStream -> {
        workflowSessions[key(userId, token)]?.let {
          LOG.debug("Stopping stream in '{}' for user '{}' with token '{}'", it.id(), userId, token)
          it.cancelStream()
        }
      }
    }
  }

  private fun key(userId: KUUID, token: KUUID) = Pair(userId, token)
}
