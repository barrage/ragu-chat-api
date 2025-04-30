package net.barrage.llmao.app.ws

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.websocket.*
import java.time.Instant
import java.util.*
import kotlin.time.Duration.Companion.seconds
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import net.barrage.llmao.app.http.queryParam
import net.barrage.llmao.app.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.Plugins
import net.barrage.llmao.core.workflow.Session
import net.barrage.llmao.core.workflow.SessionManager
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.core.workflow.emit
import net.barrage.llmao.types.KUUID

private val LOG = KtorSimpleLogger("n.b.l.a.api.ws.WebsocketRouter")

fun Application.websocketServer(manager: SessionManager, plugins: Plugins) {

  val serializer = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
    serializersModule = SerializersModule {
      polymorphic(WorkflowOutput::class) {
        subclass(WorkflowOutput.StreamChunk::class)
        subclass(WorkflowOutput.StreamComplete::class)
        with(plugins) { configureOutputSerialization() }
      }
    }
  }

  install(WebSockets) {
    // FIXME: Shrink
    maxFrameSize = Long.MAX_VALUE
    masking = false
    pingPeriod = 5.seconds
    contentConverter = KotlinxWebsocketSerializationConverter(serializer)
  }

  routing {
    // Protected WS route for one time tokens.
    authenticate("user") {
      route("/ws") {
        get(websocketGenerateToken()) {
          val user = call.user()
          val token = manager.tokens.registerToken(user)
          call.respond(HttpStatusCode.OK, token)
        }
      }
    }

    webSocket {
      // Used for debugging
      val connectionStart = Instant.now()

      // Validate session
      val token = call.queryParam("token")

      if (token == null) {
        LOG.debug("WS - closing due to missing token")
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val user = manager.tokens.removeToken(token)

      if (user == null) {
        LOG.debug("WS - closing due to no token entry")
        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
        return@webSocket
      }

      val session = Session(user, token)

      val emitter = WebsocketEmitter(this, serializer)
      manager.registerSystemEmitter(session, emitter)

      LOG.debug("{} - websocket connection open", user.id)

      try {
        for (frame in incoming) {
          if (frame !is Frame.Text) {
            LOG.warn("Unsupported frame received, {}", frame)
            continue
          }
          runCatching { manager.handleMessage(session, frame.readText(), emitter) }
            .onFailure { e ->
              LOG.error("error in websocket session", e)
              emitter.emit(e as? AppError ?: AppError.internal(e.message))
            }
        }
      } catch (e: Throwable) {
        LOG.error("error in websocket session", e)
      } finally {
        // From this point on, the websocket connection is closed
        LOG.debug(
          "Connection for user-token '{}'-'{}' closed. Duration: {}ms",
          user.id,
          token,
          Instant.now().toEpochMilli() - connectionStart.toEpochMilli(),
        )
        manager.removeWorkflow(session)
        manager.removeSystemEmitter(session)
      }
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
