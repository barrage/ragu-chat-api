package net.barrage.llmao.app.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAiChatMessage
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ConversationLlm
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.TokenChunk

const val TITLE_GENERATION_MODEL = "gpt-4"

class OpenAI(apiKey: String) : ConversationLlm {
  private val client: OpenAI = OpenAI(token = apiKey)

  override fun id(): String {
    return "openai"
  }

  override suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
      )

    // TODO: Remove yelling
    return this.client.chatCompletion(chatRequest).choices[0].message.content!!
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>> {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        streamOptions = StreamOptions(true),
      )

    return this.client.chatCompletions(chatRequest).map {
      listOf(
        TokenChunk(
          it.id,
          it.created,
          it.choices.firstOrNull()?.delta?.content ?: " ",
          it.choices.firstOrNull()?.finishReason,
        )
      )
    }
  }

  override suspend fun generateChatTitle(proompt: String, config: LlmConfig): String {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(TITLE_GENERATION_MODEL),
        messages = listOf(OpenAiChatMessage.User(proompt)),
        temperature = config.temperature,
      )

    val response = this.client.chatCompletion(chatRequest)

    // TODO: Remove yelling
    return response.choices[0].message.content!!
  }

  override suspend fun summarizeConversation(
    proompt: String,
    config: LlmConfig,
    maxTokens: Int?,
  ): String {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = listOf(OpenAiChatMessage.User(proompt)),
        maxTokens = maxTokens,
        temperature = config.temperature,
      )

    // TODO: Remove yelling
    return this.client.chatCompletion(chatRequest).choices[0].message.content!!
  }

  override fun supportsModel(model: String): Boolean {
    return when (model) {
      "gpt-3.5-turbo" -> true
      "gpt-4" -> true
      else -> false
    }
  }
}