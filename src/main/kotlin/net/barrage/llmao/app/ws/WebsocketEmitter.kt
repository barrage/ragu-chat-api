package net.barrage.llmao.app.ws

import io.ktor.server.websocket.*
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.workflow.Emitter

/** Represents a 2 way communication channel between the server and a client. */
class WebsocketEmitter(ws: WebSocketServerSession, private val converter: Json) : Emitter {
  /** Used to emit messages to the collector, which in turn forwards them to the client. */
  val flow: MutableSharedFlow<String> = MutableSharedFlow()

  val log = KtorSimpleLogger("n.b.l.a.ws.WebsocketEmitter")

  /**
   * Start the job that collects messages from the flow and forwards anything emitted to the client.
   * Once the client disconnects, the job is cancelled and the cancellation caught in the router.
   */
  init {
    ws.launch { flow.collect { ws.send(it) } }
  }

  /** Emit a system message to the client. */
  override suspend fun <T> emit(message: T, serializer: KSerializer<T>) =
    flow.emit(converter.encodeToString(serializer, message))
}
