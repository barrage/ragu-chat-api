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
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.AdapterState
import net.barrage.llmao.app.workflow.IncomingMessage
import net.barrage.llmao.app.workflow.IncomingMessageSerializer
import net.barrage.llmao.app.workflow.chat.ChatWorkflowFactory
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.queryParam
import net.barrage.llmao.plugins.user

private val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.app.api.ws.WebsocketRouter")

fun Application.websocketServer(
  factory: ChatWorkflowFactory,
  adapters: AdapterState,
  listener: EventListener<StateChangeEvent>,
) {
  val server = WebsocketSessionManager(factory = factory, listener = listener, adapters = adapters)

  val tokenManager = WebsocketTokenManager()

  install(WebSockets) {
    // FIXME: Shrink
    maxFrameSize = Long.MAX_VALUE
    masking = false
    contentConverter = KotlinxWebsocketSerializationConverter(IncomingMessageSerializer)
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

      val token =
        try {
          KUUID.fromString(rawToken)
        } catch (e: IllegalArgumentException) {
          close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
          return@webSocket
        }

      val userId = tokenManager.removeToken(token)

      if (userId == null) {
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val session = WebsocketSession(userId, token)

      val emitter = WebsocketEmitter.new<OutgoingSystemMessage>(this)
      server.registerSystemEmitter(session, emitter)

      LOG.debug("Websocket connection opened for '{}' with token '{}'", userId, token)

      runCatching {
          for (frame in incoming) {
            if (frame !is Frame.Text) {
              LOG.warn("Unsupported frame received, {}", frame)
              continue
            }

            val message =
              try {
                Json.decodeFromString<IncomingMessage>(frame.readText())
              } catch (e: Throwable) {
                e.printStackTrace()
                emitter.emitError(
                  AppError.api(ErrorReason.InvalidParameter, "Message format malformed")
                )
                continue
              }

            try {
              server.handleMessage(session, message, this)
            } catch (error: AppError) {
              emitter.emitError(error)
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
          server.removeWorkflow(session)
          server.removeSystemEmitter(session)
          return@webSocket
        }

      // From this point on, the websocket connection is closed
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
      server.removeWorkflow(session)
      server.removeSystemEmitter(session)
    }
  }
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
