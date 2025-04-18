package net.barrage.llmao.app.api.ws

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.time.Duration.Companion.seconds
import net.barrage.llmao.app.api.http.queryParam
import net.barrage.llmao.app.api.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.workflow.Session
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.workflow.SessionTokenManager
import net.barrage.llmao.core.workflow.emit
import net.barrage.llmao.types.KUUID

private val LOG = io.ktor.util.logging.KtorSimpleLogger("n.b.l.a.api.ws.WebsocketRouter")

fun Application.websocketServer(listener: EventListener<StateChangeEvent>) {
  val server = SessionManager(listener = listener)

  val tokenManager = SessionTokenManager()

  install(WebSockets) {
    // FIXME: Shrink
    maxFrameSize = Long.MAX_VALUE
    masking = false
    pingPeriod = 5.seconds
  }

  routing {
    // Protected WS route for one time tokens.
    authenticate("user") {
      route("/ws") {
        get(websocketGenerateToken()) {
          val user = call.user()
          LOG.debug("{} - registering token", user.id)
          val token = tokenManager.registerToken(user)
          call.respond(HttpStatusCode.OK, token)
        }
      }
    }

    webSocket {
      // Used for debugging
      val connectionStart = Instant.now()
      LOG.debug("Websocket connection opened ({})", connectionStart.nano)

      // Validate session
      val token = call.queryParam("token")

      if (token == null) {
        LOG.debug("WS - closing due to missing token")
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val user = tokenManager.removeToken(token)

      if (user == null) {
        LOG.debug("WS - closing due to no token entry")
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val session = Session(user, token)

      val emitter = WebsocketEmitter(this)
      server.registerSystemEmitter(session, emitter)

      LOG.debug("Websocket connection opened for '{}' with token '{}'", user.username, token)

      runCatching {
          for (frame in incoming) {
            if (frame !is Frame.Text) {
              LOG.warn("Unsupported frame received, {}", frame)
              continue
            }
            try {
              server.handleMessage(session, frame.readText(), emitter)
            } catch (error: AppError) {
              emitter.emit(error)
            }
          }
        }
        .onFailure { e ->
          if (e is AppError) {
            emitter.emit(e)
          }
          LOG.error("Websocket exception occurred {}", e.message)
          val formatter =
            DateTimeFormatter.ofPattern("yyyy/MM/dd:HH:mm:ss").withZone(ZoneId.systemDefault())
          LOG.debug(
            "Connection for user-token '{}'-'{}' closed. Start: {}, End: {}, Duration: {}ms",
            user.id,
            token,
            formatter.format(Instant.ofEpochMilli(connectionStart.toEpochMilli())),
            formatter.format(Instant.now()),
            Instant.now().toEpochMilli() - connectionStart.toEpochMilli(),
          )
          server.removeWorkflow(session)
          server.removeSystemEmitter(session)
        }

      // From this point on, the websocket connection is closed
      val formatter =
        DateTimeFormatter.ofPattern("yyyy/MM/dd:HH:mm:ss").withZone(ZoneId.systemDefault())
      LOG.debug(
        "Connection for user-token '{}'-'{}' closed. Start: {}, End: {}, Duration: {}ms",
        user.id,
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

private fun websocketGenerateToken(): RouteConfig.() -> Unit = {
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
