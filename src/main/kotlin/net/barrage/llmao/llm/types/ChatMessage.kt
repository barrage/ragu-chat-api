package net.barrage.llmao.llm.types

import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage
import net.barrage.llmao.serializers.KUUID

data class ChatMessage(
  val role: String,
  val content: String,

  /** If this message is a response to a user message, the ID of the message being responded to. */
  val responseTo: KUUID? = null,
) {
  fun toOpenAiChatMessage(): OpenAIChatMessage {
    return when (this.role) {
      "user" -> OpenAIChatMessage.User(this.content)
      "assistant" -> OpenAIChatMessage.Assistant(this.content)
      "system" -> OpenAIChatMessage.System(this.content)
      else -> throw InternalError("Unknown role")
    }
  }
}

fun userChatMessage(proompt: String) = ChatMessage("user", proompt)

fun assistantChatMessage(response: String) = ChatMessage("assistant", response)

fun systemChatMessage(response: String) = ChatMessage("system", response)
