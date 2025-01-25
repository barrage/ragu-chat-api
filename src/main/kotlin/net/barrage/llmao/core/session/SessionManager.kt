package net.barrage.llmao.core.session

import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import java.util.concurrent.ConcurrentHashMap

class SessionManager(
  private val factory: SessionFactory,
  listener: EventListener<StateChangeEvent>,
) {
  /** Maps user ID + token pairs to their sessions. */
  private val sessions: MutableMap<Pair<KUUID, KUUID>, Session> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs directly to their output channels. The channels here are used to
   * broadcast system events to all connected clients.
   */
  private val channels: MutableMap<Pair<KUUID, KUUID>, Channel> = ConcurrentHashMap()

  init {
    LOG.info("Starting WS server event listener")
    listener.start { event -> handleEvent(event) }
  }

  fun registerSession(userId: KUUID, token: KUUID, channel: Channel) {
    channels[key(userId, token)] = channel
  }

  /** Removes the session and its corresponding chat associated with the user and token pair. */
  fun removeSession(userId: KUUID, token: KUUID) {
    LOG.info("Removing session for '{}' with token '{}", userId, token)
    val channel = channels.remove(key(userId, token))
    sessions.remove(key(userId, token))

    // Has to be closed manually to stop the job from running
    channel?.close()
  }

  suspend fun handleMessage(userId: KUUID, token: KUUID, message: ClientMessage, channel: Channel) {
    when (message) {
      is ClientMessage.Chat -> handleChatMessage(userId, token, message.text)
      is ClientMessage.System -> handleSystemMessage(userId, token, message.payload, channel)
    }
  }

  /** Handle a system event from the [EventListener]. */
  private suspend fun handleEvent(event: StateChangeEvent) {
    when (event) {
      is StateChangeEvent.AgentDeactivated -> {
        LOG.info("Handling agent deactivated event ({})", event.agentId)

        sessions.values.retainAll { chat -> chat.entityId().id != event.agentId }

        for (channel in channels.values) {
          channel.emitServer(ServerMessage.AgentDeactivated(event.agentId))
        }
      }
    }
  }

  private fun handleChatMessage(userId: KUUID, token: KUUID, message: String) {
    LOG.info("Handling chat message from '{}' with token '{}': {}", userId, token, message)

    val chat =
      sessions[key(userId, token)]
        ?: throw AppError.api(
          ErrorReason.Websocket,
          "Chat not open for user '$userId' with token '$token'",
        )

    if (chat.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    LOG.debug("Starting stream in '{}' for user '{}' with token '{}'", chat.id().id, userId, token)
    chat.start(message)
  }

  private suspend fun handleSystemMessage(
    userId: KUUID,
    token: KUUID,
    message: SystemMessage,
    channel: Channel,
  ) {
    when (message) {
      is SystemMessage.OpenNewChat -> {
        val chat =
          factory.newChatSession(userId = userId, agentId = message.agentId, channel = channel)
        sessions[key(userId, token)] = chat
        channel.emitServer(ServerMessage.ChatOpen(chat.id))

        LOG.debug(
          "Opened new chat ('{}') for '{}' with token '{}', total chats: {}",
          chat.id,
          userId,
          token,
          sessions.size,
        )
      }
      is SystemMessage.OpenExistingChat -> {
        val session = sessions[key(userId, token)]

        // Prevent loading the same chat
        if (session != null && session.id().id == message.chatId) {
          LOG.debug("Existing chat has same ID as opened chat '{}'", session.id())
          channel.emitServer(ServerMessage.ChatOpen(session.id().id))
          return
        }

        session?.cancelStream()

        val existingChat =
          factory.fromExistingChat(
            id = message.chatId,
            channel = channel,
            initialHistorySize = message.initialHistorySize,
          )

        sessions[key(userId, token)] = existingChat

        channel.emitServer(ServerMessage.ChatOpen(message.chatId))

        LOG.debug(
          "Opened existing chat ('{}') for '{}' with token '{}'",
          existingChat.id,
          userId,
          token,
        )
      }
      is SystemMessage.CloseChat -> {
        sessions.remove(key(userId, token))?.let {
          channel.emitServer(ServerMessage.ChatClosed(it.id().id))
          it.cancelStream()
          LOG.debug(
            "Closed chat ('{}') for user '{}' with token '{}', total chats: {}",
            it.id().id,
            userId,
            token,
            sessions.size,
          )
        }
      }
      is SystemMessage.StopStream -> {
        sessions[key(userId, token)]?.let {
          LOG.debug(
            "Stopping stream in '{}' for user '{}' with token '{}'",
            it.id().id,
            userId,
            token,
          )
          it.cancelStream()
        }
      }
    }
  }

  private fun key(userId: KUUID, token: KUUID) = Pair(userId, token)
}
