package net.barrage.llmao.app.embeddings.openai

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import net.barrage.llmao.app.llm.EmbeddingModelDeployment
import net.barrage.llmao.app.llm.ModelDeploymentMap
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.embedding.Embeddings
import net.barrage.llmao.core.token.TokenUsageAmount

class VllmEmbedder(
  /** The base URL of the endpoint. */
  private val endpoint: String,

  /** Authorization token. */
  private val apiKey: String,

  /** Maps embedding model identifiers to VLLM instances. */
  private val deploymentMap: ModelDeploymentMap<EmbeddingModelDeployment>,
) : Embedder {
  override fun id(): String = "vllm"

  override suspend fun supportsModel(model: String): Boolean = deploymentMap.containsKey(model)

  override suspend fun embed(input: String, model: String): Embeddings {
    val request = EmbeddingRequest(ModelId(model), listOf(input))

    val response = client(model).embeddings(request)

    if (response.embeddings.isEmpty()) {
      throw AppError.internal("VLLM Embedder generated 0 embeddings")
    }

    // We always get embeddings in a 1:1 manner relative to the input
    // Above check ensures we are always in bounds
    return Embeddings(
      response.embeddings.map { it.embedding }[0],
      TokenUsageAmount(
        prompt = response.usage.promptTokens,
        completion = response.usage.completionTokens,
        total = response.usage.totalTokens,
      ),
    )
  }

  override suspend fun vectorSize(model: String): Int {
    return deploymentMap[model]?.vectorSize
      ?: throw AppError.api(ErrorReason.InvalidParameter, "Model $model not found")
  }

  private fun getDeploymentForModel(model: String): String =
    deploymentMap[model]?.deploymentId
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
    val host = OpenAIHost(baseUrl = "https://$endpoint/$deploymentId/v1/")
    return OpenAI(OpenAIConfig(host = host, headers = mapOf("api-key" to apiKey), token = apiKey))
  }
}
