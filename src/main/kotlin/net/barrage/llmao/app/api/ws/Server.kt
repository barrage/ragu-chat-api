package net.barrage.llmao.app.api.ws

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.queryParam
import net.barrage.llmao.plugins.user

fun Application.websocketServer(factory: ChatFactory) {
  val messageHandler = MessageHandler(factory)

  val tokenManager = TokenManager()

  install(WebSockets) {
    // FIXME: Shrink
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(ClientMessageSerializer)
    pingPeriod = Duration.ofSeconds(5)
  }

  routing {
    // Protected WS route for one time tokens.
    authenticate("auth-session") {
      route("/ws") {
        get(websocketGenerateToken()) {
          val user = call.user()
          val token = tokenManager.registerToken(user.id)
          call.respond(HttpStatusCode.OK, "$token")
        }
      }
    }

    // The websocket route is unprotected in regard to the regular HTTP auth mechanisms (read
    // cookies). We protect it manually with the TokenManager.
    webSocket {
      // Used for debugging
      val connectionStart = Instant.now()

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

      val userId = tokenManager.removeToken(token)

      if (userId == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val channel = channel()

      LOG.debug("Websocket connection opened for '{}' with token '{}'", userId, token)

      runCatching {
          for (frame in incoming) {
            if (frame !is Frame.Text) {
              LOG.warn("Unsupported frame received, {}", frame)
              continue
            }

            val message =
              try {
                Json.decodeFromString<ClientMessage>(frame.readText())
              } catch (e: Throwable) {
                e.printStackTrace()
                channel.emitError(
                  AppError.api(ErrorReason.InvalidParameter, "Message format malformed")
                )
                continue
              }

            try {
              messageHandler.handleMessage(userId, token, message, channel)
            } catch (error: AppError) {
              channel.emitError(error)
            }
          }
        }
        .onFailure { e ->
          LOG.error("Websocket exception occurred {}", e.message)
          val formatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd:HH:mm:ss").withZone(ZoneId.systemDefault())
          LOG.debug(
            "Connection for user-token '{}'-'{}' closed. Start: {}, End: {}, Duration: {}ms",
            userId,
            token,
            formatter.format(Instant.ofEpochMilli(connectionStart.toEpochMilli())),
            formatter.format(Instant.now()),
            Instant.now().toEpochMilli() - connectionStart.toEpochMilli(),
          )
          messageHandler.removeChat(userId, token)
          return@webSocket
        }

      // From this point on, the websocket connection is closed
      LOG.debug("Websocket connection closed for '{}' with token '{}'", userId, token)
      messageHandler.removeChat(userId, token)

      // Has to be closed manually to stop the job from running
      channel.close()
    }
  }
}

/** Open a new channel and start running it in a coroutine. */
private fun DefaultWebSocketServerSession.channel(): Channel {
  val flow = MutableSharedFlow<String>()
  val job = launch {
    flow.onCompletion { LOG.debug("Channel successfully closed") }.collect { send(it) }
  }
  return Channel(flow, job)
}

private fun websocketGenerateToken(): OpenApiRoute.() -> Unit = {
  tags("ws")
  description = "Generate a one-time token for WebSocket connection"
  summary = "Generate WebSocket token"
  response {
    HttpStatusCode.OK to
      {
        this.body<UUID> { description = "Token for WebSocket connection" }
        description = "Token for WebSocket connection"
        body<KUUID> { example("example") { value = KUUID.randomUUID() } }
      }
    HttpStatusCode.Unauthorized to
      {
        description = "Unauthorized"
        body<List<AppError>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while generating token"
        body<List<AppError>> {}
      }
  }
}
