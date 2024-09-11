package net.barrage.llmao.websocket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.enums.Languages
import net.barrage.llmao.serializers.KUUID

@Serializable
sealed class WSMessage {
    abstract val userId: KUUID
    abstract val type: WSMessageType
}

@Serializable
@SerialName("system")
data class WSSystemMessage(
    override val userId: KUUID,
    override val type: WSMessageType = WSMessageType.SYSTEM,
    val payload: WSSystemMessagePayload
) : WSMessage()

@Serializable
@SerialName("chat")
data class WSChatMessage(
    override val userId: KUUID,
    override val type: WSMessageType = WSMessageType.CHAT,
    val payload: String
) : WSMessage()

enum class WSMessageType {
    @SerialName("system")
    SYSTEM,

    @SerialName("chat")
    CHAT;
}

@Serializable
class WSSystemMessagePayload(
    val header: String,
    val body: WSSystemMessageBody? = null,
)

@Serializable
class WSSystemMessageBody(
    val chatId: KUUID? = null,
    val agentId: Int? = null,
    val llm: LLMModels? = null,
    val language: Languages? = null,
    val title: String? = null,
)

