package net.barrage.llmao.websocket

import kotlinx.coroutines.flow.MutableSharedFlow
import net.barrage.llmao.error.Error
import net.barrage.llmao.llm.types.TokenChunk
import net.barrage.llmao.serializers.KUUID

class Emitter(private val messageResponseFlow: MutableSharedFlow<String>) {
  suspend fun emitChunk(chunk: TokenChunk) {
    messageResponseFlow.emit(chunk.content ?: "")
  }

  suspend fun emitFinishResponse(event: FinishEvent) {
    messageResponseFlow.emit(event.toString())
  }

  suspend fun emitError(error: Error) {
    messageResponseFlow.emit(error.toString())
  }

  suspend fun emitServerMessage(message: ServerMessage) {
    messageResponseFlow.emit(message.toString())
  }

  suspend fun emitTitle(chatId: KUUID, title: String) {
    messageResponseFlow.emit(ServerMessage.ChatTitle(chatId, title).toString())
  }
}
