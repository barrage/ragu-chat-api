package net.barrage.llmao.app.api.ws

import java.util.concurrent.ConcurrentHashMap
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class WebsocketServer(
  private val factory: WebsocketChatFactory,
  listener: EventListener<StateChangeEvent>,
) {
  /**
   * Maps user ID + token pairs to their chat instances. Chats encapsulate the websocket connection
   * with all the parameters necessary to maintain a chat instance.
   */
  private val chats: MutableMap<Pair<KUUID, KUUID>, Chat> = ConcurrentHashMap()

  /**
   * Maps user ID + token pairs to their websocket channels. The websocket channels are stored
   * directly in the map (they use the same connection as their respective chats). The channels here
   * are used to broadcast system events to all connected clients.
   */
  private val sessions: MutableMap<Pair<KUUID, KUUID>, WebsocketChannel> = ConcurrentHashMap()

  init {
    LOG.info("Starting WS server event listener")
    listener.start { event -> handleEvent(event) }
  }

  fun registerSession(userId: KUUID, token: KUUID, channel: WebsocketChannel) {
    sessions[key(userId, token)] = channel
  }

  /** Removes the session and its corresponding chat associated with the user and token pair. */
  fun removeSession(userId: KUUID, token: KUUID) {
    LOG.info("Removing session for '{}' with token '{}", userId, token)
    val channel = sessions.remove(key(userId, token))
    chats.remove(key(userId, token))

    // Has to be closed manually to stop the job from running
    channel?.close()
  }

  suspend fun handleMessage(
    userId: KUUID,
    token: KUUID,
    message: ClientMessage,
    channel: WebsocketChannel,
  ) {
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

        chats.values.retainAll { chat -> chat.agentId != event.agentId }

        for (channel in sessions.values) {
          channel.emitServer(ServerMessage.AgentDeactivated(event.agentId))
        }
      }
    }
  }

  private fun handleChatMessage(userId: KUUID, token: KUUID, message: String) {
    LOG.info("Handling chat message from '{}' with token '{}': {}", userId, token, message)

    val chat =
      chats[key(userId, token)]
        ?: throw AppError.api(
          ErrorReason.Websocket,
          "Chat not open for user '$userId' with token '$token'",
        )

    if (chat.isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    LOG.debug("Starting stream in '{}' for user '{}' with token '{}'", chat.id, userId, token)
    chat.startStreaming(message)
  }

  private suspend fun handleSystemMessage(
    userId: KUUID,
    token: KUUID,
    message: SystemMessage,
    channel: WebsocketChannel,
  ) {
    when (message) {
      is SystemMessage.OpenNewChat -> {
        val chat = factory.new(userId = userId, agentId = message.agentId, channel = channel)
        chats[key(userId, token)] = chat
        channel.emitServer(ServerMessage.ChatOpen(chat.id))

        LOG.debug(
          "Opened new chat ('{}') for '{}' with token '{}', total chats: {}",
          chat.id,
          userId,
          token,
          chats.size,
        )
      }
      is SystemMessage.OpenExistingChat -> {
        val chat = chats[key(userId, token)]

        // Prevent loading the same chat
        if (chat != null && chat.id == message.chatId) {
          LOG.debug("Existing chat has same ID as opened chat '{}'", chat.id)
          channel.emitServer(ServerMessage.ChatOpen(chat.id))
          return
        }

        chat?.cancelStream()

        val existingChat =
          factory.fromExisting(
            id = message.chatId,
            channel = channel,
            initialHistorySize = message.initialHistorySize,
          )

        chats[key(userId, token)] = existingChat

        channel.emitServer(ServerMessage.ChatOpen(message.chatId))

        LOG.debug(
          "Opened existing chat ('{}') for '{}' with token '{}'",
          existingChat.id,
          userId,
          token,
        )
      }
      is SystemMessage.CloseChat -> {
        chats.remove(key(userId, token))?.let {
          channel.emitServer(ServerMessage.ChatClosed(it.id))
          it.cancelStream()
          LOG.debug(
            "Closed chat ('{}') for user '{}' with token '{}', total chats: {}",
            it.id,
            userId,
            token,
            chats.size,
          )
        }
      }
      is SystemMessage.StopStream -> {
        chats[key(userId, token)]?.let {
          LOG.debug("Stopping stream in '{}' for user '{}' with token '{}'", it.id, userId, token)
          it.cancelStream()
        }
      }
    }
  }

  private fun key(userId: KUUID, token: KUUID) = Pair(userId, token)
}
