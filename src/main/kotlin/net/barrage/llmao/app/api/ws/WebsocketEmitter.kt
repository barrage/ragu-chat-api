package net.barrage.llmao.app.api.ws

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.workflow.Emitter

private val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.app.api.ws.WebsocketEmitter")

/** Represents a 2 way communication channel between the server and a client. */
class WebsocketEmitter<T>(ws: WebSocketServerSession, private val serialize: (T) -> String) :
  Emitter<T> {
  /** Used to emit messages to the collector, which in turn forwards them to the client. */
  private val flow: MutableSharedFlow<String> = MutableSharedFlow()

  /**
   * Start the job that collects messages from the flow and forwards anything emitted to the client.
   * Once the client disconnects, the job is cancelled and the cancellation caught in the router.
   */
  init {
    ws.launch { flow.collect { ws.send(it) } }
  }

  /** Emit an application error. */
  override suspend fun emitError(error: AppError) {
    LOG.error("Emitting error", error)
    flow.emit(Json.encodeToString(error))
  }

  /** Emit a system message to the client. */
  override suspend fun emit(message: T) {
    LOG.trace("Emitting server message: {}", message)
    flow.emit(serialize(message))
  }

  companion object {
    inline fun <reified T> new(ws: WebSocketServerSession): WebsocketEmitter<T> {
      return WebsocketEmitter(ws) { message: T -> Json.encodeToString(message) }
    }
  }
}
