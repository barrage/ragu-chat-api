package net.barrage.llmao.app.api.ws

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.error.AppError

class Emitter(private val messageResponseFlow: MutableSharedFlow<String>) {
  suspend fun emitChunk(chunk: TokenChunk) {
    messageResponseFlow.emit(chunk.content ?: "")
  }

  suspend fun emitError(error: AppError) {
    messageResponseFlow.emitJson(error)
  }

  suspend fun emitServer(message: ServerMessage) {
    messageResponseFlow.emitJson(message)
  }

  suspend fun emitStop() {
    messageResponseFlow.emit("##STOP##")
  }
}

private suspend inline fun <reified T> MutableSharedFlow<String>.emitJson(input: T) {
  this.emit(Json.encodeToString(input))
}
