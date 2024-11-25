package net.barrage.llmao.app.api.ws

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.error.AppError

/**
 * Encapsulates a [MutableSharedFlow] obtained when a client connects to the websocket server and
 * the [Job] used to spawn it.
 *
 * Represents a 2 way communication channel between a chat and client.
 *
 * @param flow The flow to emit messages to the client.
 * @param job The shared flow emitter job to cancel when the client disconnects.
 */
class Channel(private val flow: MutableSharedFlow<String>, private val job: Job) {
  /** Emit the textual content of a chunk obtained from streaming LLM inference. */
  suspend fun emitChunk(chunk: TokenChunk) {
    chunk.content?.let { flow.emit(it) }
  }

  /** Emit an application error. */
  suspend fun emitError(error: AppError) {
    LOG.debug("Emitting error", error)
    flow.emitJson(error)
  }

  /** Emit a server message. */
  suspend fun emitServer(message: ServerMessage) {
    LOG.debug("Emitting server message: {}", message)
    flow.emitJson(message)
  }

  /** Cancel the job containing the shared flow. Called on disconnects. */
  fun close() {
    job.cancel()
  }
}

private suspend inline fun <reified T> MutableSharedFlow<String>.emitJson(input: T) {
  this.emit(Json.encodeToString(input))
}
