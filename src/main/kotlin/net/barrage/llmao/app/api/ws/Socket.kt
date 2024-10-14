package net.barrage.llmao.app.api.ws

import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.queryParam
import net.barrage.llmao.plugins.user

fun Application.websocketServer(server: Server) {
  install(WebSockets) {
    // FIXME: Shrink
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
  }

  routing {
    // Protected WS route for one time tokens.
    authenticate("auth-session") {
      route("/ws") {
        get {
          val user = call.user()
          val token = server.registerToken(user.id)
          call.respond(HttpStatusCode.OK, "$token")
        }
      }
    }

    webSocket {
      // Validate session
      val rawToken = call.queryParam("token")

      if (rawToken == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val token: KUUID
      try {
        token = KUUID.fromString(rawToken)
      } catch (e: IllegalArgumentException) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val userId = server.removeToken(token)

      if (userId == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
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
              sendSerialized(AppError.api(ErrorReason.InvalidParameter, "Message format malformed"))
              continue
            }

            try {
              server.handleMessage(userId, message, emitter)
            } catch (error: AppError) {
              sendSerialized(error)
            }
          }

          is Frame.Close -> {
            job.cancel()
            server.removeChat(userId)
          }

          else -> {
            sendSerialized(
              AppError.api(ErrorReason.InvalidParameter, "Only text messages are allowed")
            )
          }
        }
      }
    }
  }
}
