package net.barrage.llmao.app.llm.openai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.barrage.llmao.core.llm.ChatCompletion
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.InferenceProvider

private val SUPPORTED_MODELS = listOf("Qwen/Qwen2.5-1.5B-Instruct")

class Vllm(endpoint: String, apiKey: String) : InferenceProvider {
  private val client: OpenAI =
    OpenAI(
      token = apiKey,
      host = OpenAIHost(endpoint),
      logging = LoggingConfig(logLevel = LogLevel.Info),
    )

  override fun id(): String {
    return "vllm"
  }

  override suspend fun chatCompletion(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): ChatCompletion {
    val chatRequest =
      ChatCompletionRequest(
        messages = messages.map { it.toOpenAiChatMessage() },
        model = ModelId(config.model),
        temperature = config.temperature,
        presencePenalty = config.presencePenalty,
        maxTokens = config.maxTokens,
        tools = config.tools?.map { it.toOpenAiTool() },
      )

    return client.chatCompletion(chatRequest).toNativeChatCompletion()
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): Flow<ChatMessageChunk> {
    val chatRequest =
      ChatCompletionRequest(
        messages = messages.map { it.toOpenAiChatMessage() },
        streamOptions = StreamOptions(true),
        model = ModelId(config.model),
        temperature = config.temperature,
        presencePenalty = config.presencePenalty,
        maxTokens = config.maxTokens,
        tools = config.tools?.map { it.toOpenAiTool() },
      )

    return this.client.chatCompletions(chatRequest).map { chunk -> chunk.toNativeMessageChunk() }
  }

  override suspend fun supportsModel(model: String): Boolean {
    return SUPPORTED_MODELS.contains(model)
  }

  override suspend fun listModels(): List<String> {
    return SUPPORTED_MODELS
  }
}
