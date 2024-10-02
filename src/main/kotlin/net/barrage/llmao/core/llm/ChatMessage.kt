package net.barrage.llmao.core.llm

import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.types.KUUID

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

  companion object {
    fun fromModel(model: Message): ChatMessage {
      return ChatMessage(model.senderType, model.content, model.responseTo)
    }

    fun user(content: String): ChatMessage {
      return ChatMessage("user", content)
    }

    fun assistant(content: String): ChatMessage {
      return ChatMessage("assistant", content)
    }

    fun system(content: String): ChatMessage {
      return ChatMessage("system", content)
    }
  }
}
