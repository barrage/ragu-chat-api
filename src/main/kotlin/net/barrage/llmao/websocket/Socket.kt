package net.barrage.llmao.websocket

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.apiError
import net.barrage.llmao.plugins.sessionId
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.SessionService

fun Application.websocketServer(server: Server) {
  install(WebSockets) {
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
  }

  // Keeps track of currently open chats

  // TODO: Remove when we have user context
  val sessionService = SessionService()

  routing {
    authenticate("auth-session") {
      webSocket {
        // Validate session
        val sessionId = call.sessionId()

        val userId: KUUID
        val session = sessionService.get(sessionId)

        if (session == null) {
          // TODO: logs
          send(apiError("Unauthorized").toString())
          close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
          return@webSocket
        }

        if (!session.isValid()) {
          // TODO: logs
          send(apiError("Unauthorized").toString())
          close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Session expired"))
          return@webSocket
        }

        // Handles connection concurrently
        val messageResponseFlow = MutableSharedFlow<String>()
        val sharedFlow = messageResponseFlow.asSharedFlow()
        val job = launch { sharedFlow.collect { send(it) } }

        val emitter = Emitter(messageResponseFlow)

        for (frame in incoming) {
          when (frame) {
            is Frame.Text -> {
              val message: ClientMessage

              try {
                message = Json.decodeFromString(frame.readText())
              } catch (e: Throwable) {
                e.printStackTrace()
                send(apiError("Unprocessable Entity", "Message format malformed").toString())
                continue
              }

              server.handleMessage(session.userId, message, emitter)
            }

            is Frame.Close -> {
              job.cancel()
              server.removeChat(session.userId)
              close(CloseReason(CloseReason.Codes.NORMAL, "Session closed"))
              return@webSocket
            }

            else -> {
              send(apiError("Invalid message format", "Only text messages are allowed").toString())
            }
          }
        }
      }
    }
  }
}
