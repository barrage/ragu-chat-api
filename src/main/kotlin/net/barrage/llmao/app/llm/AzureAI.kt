package net.barrage.llmao.app.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage as OpenAIChatMessage
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ConversationLlm
import net.barrage.llmao.core.llm.LlmConfig
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AzureAI(
  private val apiKey: String,
  private val endpoint: String,
  private val apiVersion: String,
) : ConversationLlm {
  private var modelMap = mapOf("gpt-3.5-turbo" to "gpt-35-turbo", "gpt-4" to "gpt-4")

  override fun id(): String {
    return "azure"
  }

  override suspend fun chatCompletion(messages: List<ChatMessage>, config: LlmConfig): String {
    val client = getClient(config.model)
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(modelMap[config.model]!!),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
      )

    // TODO: Remove yelling
    return client.chatCompletion(chatRequest).choices[0].message.content!!
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: LlmConfig,
  ): Flow<List<TokenChunk>> {
    val client = getClient(config.model)

    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(modelMap[config.model]!!),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        streamOptions = StreamOptions(true),
      )

    return client.chatCompletions(chatRequest).map {
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
    val client = getClient(config.model)

    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(modelMap[config.model]!!),
        messages = listOf(OpenAIChatMessage.User(proompt)),
        temperature = config.temperature,
      )

    val response = client.chatCompletion(chatRequest)

    // TODO: Remove yelling
    return response.choices[0].message.content!!
  }

  override suspend fun summarizeConversation(
    proompt: String,
    config: LlmConfig,
    maxTokens: Int?,
  ): String {
    val client = getClient(config.model)
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(modelMap[config.model]!!),
        messages = listOf(OpenAIChatMessage.User(proompt)),
        maxTokens = maxTokens,
        temperature = config.temperature,
      )

    // TODO: Remove yelling
    return client.chatCompletion(chatRequest).choices[0].message.content!!
  }

  override fun supportsModel(model: String): Boolean {
    return when (model) {
      "gpt-3.5-turbo" -> true
      "gpt-4" -> true
      else -> false
    }
  }

  private fun getClient(model: String): OpenAI {
    if (!modelMap.containsKey(model)) {
      throw AppError.api(
        ErrorReason.InvalidParameter,
        "LLM provider ${id()} does not support model '$model'",
      )
    }

    return OpenAI(
      OpenAIConfig(
        host =
          OpenAIHost.azure(
            resourceName = endpoint,
            deploymentId = modelMap[model]!!,
            apiVersion = apiVersion,
          ),
        headers = mapOf("api-key" to apiKey),
        token = apiKey,
      )
    )
  }
}
