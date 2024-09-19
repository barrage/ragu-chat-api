package net.barrage.llmao.websocket

import kotlinx.coroutines.flow.MutableSharedFlow
import net.barrage.llmao.error.Error
import net.barrage.llmao.llm.types.TokenChunk
import net.barrage.llmao.serializers.KUUID

const val TERMINATOR = "##STOP##"

class Emitter(
    private val messageResponseFlow: MutableSharedFlow<String>
) {
    suspend fun emitChunk(chunk: TokenChunk) {
        messageResponseFlow.emit(chunk.content ?: "")
    }

    suspend fun emitTerminator() {
        messageResponseFlow.emit(TERMINATOR)
    }

    suspend fun emitFinishResponse(event: FinishEvent) {
        messageResponseFlow.emit(TERMINATOR)
        messageResponseFlow.emit(S2CFinishEvent(event).toString())
    }

    suspend fun emitError(error: Error) {
        messageResponseFlow.emit(error.toString())
    }

    suspend fun emitSystemMessage(message: S2CMessage) {
        messageResponseFlow.emit(message.toString())
    }

    suspend fun emitTitle(chatId: KUUID, title: String) {
        messageResponseFlow.emit(S2CTitle(TitleBody(chatId, title)).toString())
    }
}
