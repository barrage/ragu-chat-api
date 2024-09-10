package net.barrage.llmao.llm

import net.barrage.llmao.serializers.KUUID
import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage

data class ChatMessage(
    val role: String,
    val message: String,

    /**
     * If this message is a response to a user message, the ID of the message being responded to.
     */
    val responseTo: KUUID? = null
) {
    fun toOpenAiChatMessage(): OpenAIChatMessage {
        return when (this.role) {
            "user" -> OpenAIChatMessage.User(this.message)
            "assistant" -> OpenAIChatMessage.Assistant(this.message)
            "system" -> OpenAIChatMessage.System(this.message)
            else -> throw InternalError("Unknown role")
        }
    }
}