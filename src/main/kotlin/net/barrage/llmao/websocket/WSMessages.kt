package net.barrage.llmao.websocket

import com.aallam.openai.api.core.FinishReason
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.enums.Languages
import net.barrage.llmao.llm.types.ChatConfig
import net.barrage.llmao.serializers.C2SMessageSerializer
import net.barrage.llmao.serializers.KUUID

@Serializable(with = C2SMessageSerializer::class)
@SerialName("c2s")
sealed class C2SMessage {
    abstract val userId: KUUID
    abstract val type: C2SMessageType
}

@Serializable
@SerialName("c2s-chat")
data class C2SChatMessage(
    override val userId: KUUID,
    override val type: C2SMessageType = C2SMessageType.CHAT,
    val payload: String
) : C2SMessage()

@Serializable
enum class C2SMessageType {
    @SerialName("system")
    SYSTEM,

    @SerialName("chat")
    CHAT;
}

@Serializable
sealed class C2SMessagePayload {
    abstract val header: C2SMessagePayloadHeader
}

@Serializable
enum class C2SMessagePayloadHeader {
    @SerialName("chat")
    CHAT,

    @SerialName("open_chat")
    OPEN_CHAT,

    @SerialName("close_chat")
    CLOSE_CHAT,

    @SerialName("delete_chat")
    DELETE_CHAT,

    @SerialName("stop_stream")
    STOP_STREAM,

    @SerialName("update_title")
    UPDATE_TITLE;
}

@Serializable
@SerialName("c2s-system-open_chat")
class C2SServerMessageOpenChat(
    override val userId: KUUID,
    override val type: C2SMessageType = C2SMessageType.SYSTEM,
    val payload: C2SMessagePayloadOpenChat
) : C2SMessage()

@Serializable
class C2SMessagePayloadOpenChat(
    override val header: C2SMessagePayloadHeader = C2SMessagePayloadHeader.OPEN_CHAT,
    val body: C2SMessagePayloadOpenChatBody
) : C2SMessagePayload()

@Serializable
class C2SMessagePayloadOpenChatBody(
    var chatId: KUUID? = null,
    val agentId: Int? = null,
    val language: Languages? = null,
    val llm: LLMModels? = null,
)

@Serializable
@SerialName("c2s-system-close_chat")
class C2SServerMessageCloseChat(
    override val userId: KUUID,
    override val type: C2SMessageType = C2SMessageType.SYSTEM,
    val payload: C2SMessagePayloadCloseChat
) : C2SMessage()

@Serializable
class C2SMessagePayloadCloseChat(
    override val header: C2SMessagePayloadHeader = C2SMessagePayloadHeader.CLOSE_CHAT
) : C2SMessagePayload()

@Serializable
@SerialName("c2s-system-delete_chat")
class C2SServerMessageDeleteChat(
    override val userId: KUUID,
    override val type: C2SMessageType = C2SMessageType.SYSTEM,
    val payload: C2SMessagePayloadDeleteChat
) : C2SMessage()

@Serializable
class C2SMessagePayloadDeleteChat(
    override val header: C2SMessagePayloadHeader = C2SMessagePayloadHeader.DELETE_CHAT,
    val body: C2SMessagePayloadDeleteChatBody
) : C2SMessagePayload()

@Serializable
class C2SMessagePayloadDeleteChatBody(
    val chatId: KUUID
)

@Serializable
@SerialName("c2s-system-stop_stream")
class C2SServerMessageStopStream(
    override val userId: KUUID,
    override val type: C2SMessageType = C2SMessageType.SYSTEM,
    val payload: C2SMessagePayloadStopStream
) : C2SMessage()

@Serializable
class C2SMessagePayloadStopStream(
    override val header: C2SMessagePayloadHeader = C2SMessagePayloadHeader.STOP_STREAM
) : C2SMessagePayload()

@Serializable
@SerialName("c2s-system-update_title")
class C2SServerMessageUpdateTitle(
    override val userId: KUUID,
    override val type: C2SMessageType = C2SMessageType.SYSTEM,
    val payload: C2SMessagePayloadUpdateTitle
) : C2SMessage()

@Serializable
class C2SMessagePayloadUpdateTitle(
    override val header: C2SMessagePayloadHeader = C2SMessagePayloadHeader.UPDATE_TITLE,
    val body: C2SMessagePayloadUpdateTitleBody
) : C2SMessagePayload()

@Serializable
class C2SMessagePayloadUpdateTitleBody(
    val chatId: KUUID,
    val title: String
)

@Serializable
sealed class S2CMessage(
    val header: S2CHeader,
) {
    abstract val body: S2CMessageBody?
}

@Serializable
sealed class S2CMessageBody

@Serializable
class S2CChatOpenMessage(
    override val body: ChatOpen
) : S2CMessage(S2CHeader.CHAT_OPEN) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

@Serializable
class ChatOpen(
    val chatId: KUUID,
    val config: ChatConfig
) : S2CMessageBody()

@Serializable
class S2CChatClosedMessage(
    override val body: Nothing? = null
) : S2CMessage(S2CHeader.CHAT_CLOSED) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

@Serializable
class S2CChatDeletedMessage(
    override val body: ChatDeleted
) : S2CMessage(S2CHeader.CHAT_DELETED) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

@Serializable
class ChatDeleted(
    val chatId: KUUID
) : S2CMessageBody()

@Serializable
class S2CTitleUpdatedMessage(
    override val body: ChatTitleUpdated
) : S2CMessage(S2CHeader.CHAT_TITLE) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

@Serializable
class ChatTitleUpdated(
    val chatId: KUUID,
    val title: String,
) : S2CMessageBody()

@Serializable
class S2CFinishEvent(
    override val body: FinishEvent
) : S2CMessage(S2CHeader.CHAT_RESPONSE) {
    override fun toString(): String {
        return Json.encodeToString(this)
    }
}

@Serializable
class FinishEvent(
    val chatId: KUUID,
    val messageId: KUUID,
    var content: String? = null,
    val finishReason: FinishReason? = null,
) : S2CMessageBody()

@Serializable
enum class S2CHeader {
    @SerialName("chat_open")
    CHAT_OPEN,

    @SerialName("chat_closed")
    CHAT_CLOSED,

    @SerialName("chat_deleted")
    CHAT_DELETED,

    @SerialName("chat_response")
    CHAT_RESPONSE,

    @SerialName("chat_title")
    CHAT_TITLE;
}
