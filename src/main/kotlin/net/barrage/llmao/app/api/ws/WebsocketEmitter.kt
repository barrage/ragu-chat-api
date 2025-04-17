package net.barrage.llmao.app.api.ws

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.workflow.chat.ChatWorkflowMessage
import net.barrage.llmao.core.workflow.Emitter

val json = Json {
  ignoreUnknownKeys = true
  encodeDefaults = true
}

/** Represents a 2 way communication channel between the server and a client. */
class WebsocketEmitter(ws: WebSocketServerSession) : Emitter {
  /** Used to emit messages to the collector, which in turn forwards them to the client. */
  val flow: MutableSharedFlow<String> = MutableSharedFlow()

  val log = io.ktor.util.logging.KtorSimpleLogger("n.b.l.a.api.ws.WebsocketEmitter")

  /**
   * Start the job that collects messages from the flow and forwards anything emitted to the client.
   * Once the client disconnects, the job is cancelled and the cancellation caught in the router.
   */
  init {
    ws.launch { flow.collect { ws.send(it) } }
  }

  /** Emit a system message to the client. */
  override suspend fun <T> emit(message: T, serializer: KSerializer<T>) {
    if (message !is ChatWorkflowMessage.StreamChunk) {
      if (message is Throwable) {
        @Suppress("LoggingPlaceholderCountMatchesArgumentCount") log.warn("Emitting error", message)
      } else {
        log.debug("Emitting message: {}", message)
      }
    }
    flow.emit(json.encodeToString(serializer, message))
  }
}
