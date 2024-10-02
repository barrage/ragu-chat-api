package net.barrage.llmao.websocket

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.Error

class Emitter(private val messageResponseFlow: MutableSharedFlow<String>) {
  suspend fun emitChunk(chunk: TokenChunk) {
    messageResponseFlow.emit(chunk.content ?: "")
  }

  suspend fun emitFinishResponse(event: FinishEvent) {
    messageResponseFlow.emitJson(event)
  }

  suspend fun emitError(error: Error) {
    messageResponseFlow.emitJson(error)
  }

  suspend fun emitServerMessage(message: ServerMessage) {
    messageResponseFlow.emitJson(message)
  }

  suspend fun emitTitle(chatId: KUUID, title: String) {
    messageResponseFlow.emitJson(ServerMessage.ChatTitle(chatId, title))
  }
}

private suspend inline fun <reified T> MutableSharedFlow<String>.emitJson(input: T) {
  this.emit(Json.encodeToString(input))
}
