package net.barrage.llmao.app.llm

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AzureAI(
  /** The `resourceId` identifying the Azure resource. */
  private val endpoint: String,

  /** Authorization token. */
  private val apiKey: String,

  /** Which version of the API to use. */
  private val apiVersion: String,
) : LlmProvider {
  /** Maps LLM identifiers to Azure deployment names. */
  private var deploymentMap =
    mapOf("gpt-3.5-turbo" to "gpt-35-turbo", "gpt-4" to "gpt-4", "gpt-4o" to "gpt-4o")

  override fun id(): String {
    return "azure"
  }

  override suspend fun chatCompletion(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): ChatMessage {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
      )

    return client(config.model)
      .chatCompletion(chatRequest)
      .toNativeChatCompletion()
      .choices
      .first()
      .message
  }

  override suspend fun completionStream(
    messages: List<ChatMessage>,
    config: ChatCompletionParameters,
  ): Flow<ChatMessageChunk> {
    val chatRequest =
      ChatCompletionRequest(
        model = ModelId(config.model),
        messages = messages.map { it.toOpenAiChatMessage() },
        temperature = config.temperature,
        maxTokens = config.maxTokens,
        tools = config.tools?.map { it.toOpenAiTool() },
        streamOptions = StreamOptions(true),
        maxTokens = config.maxCompletionTokens,
        presencePenalty = config.presencePenalty,
      )

    return client(config.model).chatCompletions(chatRequest).map { it.toNativeMessageChunk() }
  }

  override suspend fun supportsModel(model: String): Boolean {
    return deploymentMap[model] != null
  }

  override suspend fun listModels(): List<String> {
    return deploymentMap.keys.toList()
  }

  private fun getModel(model: String): String {
    return deploymentMap[model]
      ?: throw AppError.api(
        ErrorReason.InvalidParameter,
        "LLM provider ${id()} does not support model '$model'",
      )
  }

  /**
   * Deployment IDs are tied to models which means we have to instantiate a new client per model
   * inference.
   */
  private fun client(model: String): OpenAI {
    val deploymentId = getModel(model)
    val host =
      OpenAIHost(
        baseUrl = "https://$endpoint.openai.azure.com/openai/deployments/$deploymentId/",
        queryParams = mapOf("api-version" to apiVersion),
      )
    return OpenAI(OpenAIConfig(host = host, headers = mapOf("api-key" to apiKey), token = apiKey))
  }
}
