package net.barrage.llmao.app.llm.openai

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.StreamOptions
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import net.barrage.llmao.app.llm.DeploymentId
import net.barrage.llmao.app.llm.ModelDeploymentMap
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.llm.ChatCompletion
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.InferenceProvider

class AzureAI(
  /** The `resourceId` identifying the Azure resource. */
  private val endpoint: String,

  /** Authorization token. */
  private val apiKey: String,

  /** Which version of the API to use. */
  private val apiVersion: String,

  /** Maps LLM identifiers to Azure deployment names. */
  private val deploymentMap: ModelDeploymentMap<DeploymentId>,
) : InferenceProvider {

  override fun id(): String = "azure"

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

    return client(config.model).chatCompletion(chatRequest).toNativeChatCompletion()
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

    return client(config.model).chatCompletions(chatRequest).map { it.toNativeMessageChunk() }
  }

  override suspend fun supportsModel(model: String): Boolean = deploymentMap.containsKey(model)

  override suspend fun listModels(): List<String> = deploymentMap.listModels()

  private fun getDeploymentForModel(model: String): String =
    deploymentMap[model]?.id
      ?: throw AppError.api(
        ErrorReason.InvalidParameter,
        "LLM provider ${id()} does not support model '$model'",
      )

  /**
   * Deployment IDs are tied to models which means we have to instantiate a new client per model
   * inference.
   */
  private fun client(model: String): OpenAI {
    val deploymentId = getDeploymentForModel(model)
    val host =
      OpenAIHost(
        baseUrl = "https://$endpoint.openai.azure.com/openai/deployments/$deploymentId/",
        queryParams = mapOf("api-version" to apiVersion),
      )
    return OpenAI(OpenAIConfig(host = host, headers = mapOf("api-key" to apiKey), token = apiKey))
  }
}
