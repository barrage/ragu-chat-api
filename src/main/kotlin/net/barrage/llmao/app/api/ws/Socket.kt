package net.barrage.llmao.app.api.ws

import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.user

fun Application.websocketServer(server: Server) {
  install(WebSockets) {
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
  }

  routing {
    authenticate("auth-session") {
      webSocket {
        // Validate session
        val user = call.user()

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
                sendJson(AppError.api(ErrorReason.InvalidParameter, "Message format malformed"))
                continue
              }

              server.handleMessage(user.id, message, emitter)
            }

            is Frame.Close -> {
              job.cancel()
              server.removeChat(user.id)
              close(CloseReason(CloseReason.Codes.NORMAL, "Session closed"))
              return@webSocket
            }

            else -> {
              sendJson(AppError.api(ErrorReason.InvalidParameter, "Only text messages are allowed"))
            }
          }
        }
      }
    }
  }
}

suspend inline fun <reified T> WebSocketSession.sendJson(data: T) {
  send(Json.encodeToString(data))
}
