package net.barrage.llmao.websocket

import com.aallam.openai.api.core.FinishReason
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.error.Error
import net.barrage.llmao.llm.types.TokenChunk
import net.barrage.llmao.serializers.KUUID

class Emitter(
    private val session: WebSocketServerSession
) {
    suspend fun emitChunk(chunk: TokenChunk) {
        session.send(Frame.Text(chunk.content ?: ""))
    }

    suspend fun emitFinishResponse(event: FinishEvent) {
        session.send(Frame.Text("##STOP##"))
        session.send(Frame.Text("{\"header\":\"chat_response\",\"body\":\"${event}\"}"))
    }

    suspend fun emitError(error: Error) {
        session.send(error.toString())
    }

    suspend fun emitForwardTitle(event: TitleEvent) {
        session.send(Frame.Text("{\"header\":\"chat_title\",\"body\":\"${event}\"}"))
    }
}

@Serializable
class FinishEvent(
    val chatId: KUUID,
    val messageId: KUUID,
    var content: String?,
    val finishReason: FinishReason
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

@Serializable
class TitleEvent(
    val chatId: KUUID,
    val title: String
) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}
