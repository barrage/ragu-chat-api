package net.barrage.llmao.websocket

import io.ktor.server.websocket.*
import io.ktor.websocket.*
import net.barrage.llmao.error.Error
import net.barrage.llmao.llm.types.TokenChunk

const val TERMINATOR = "##STOP##"

class Emitter(
    private val session: WebSocketServerSession
) {
    suspend fun emitChunk(chunk: TokenChunk) {
        session.send(Frame.Text(chunk.content ?: ""))
    }

    suspend fun emitTerminator() {
        session.send(Frame.Text(TERMINATOR))
    }

    suspend fun emitFinishResponse(event: FinishEvent) {
        session.send(Frame.Text(TERMINATOR))
        session.send(Frame.Text(S2CFinishEvent(event).toString()))
    }

    suspend fun emitError(error: Error) {
        session.send(error.toString())
    }

    suspend fun emitSystemMessage(message: S2CMessage) {
        session.send(Frame.Text(message.toString()))
    }
}
