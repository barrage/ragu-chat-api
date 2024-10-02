package net.barrage.llmao.app.embeddings

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.apiError
import net.barrage.llmao.error.internalError

private const val DEPLOYMENT_ID = "gipitty-text-embedding-ada-002"

private enum class OpenAiModel(val value: String) {
  TextEmbedding3Large("text-embedding-3-large"),
  TextEmbedding3Small("text-embedding-3-small"),
  TextEmbeddingAda002("text-embedding-ada-002");

  companion object {
    fun tryFromString(value: String): OpenAiModel? {
      return when (value) {
        TextEmbedding3Large.value -> TextEmbedding3Large
        TextEmbedding3Small.value -> TextEmbedding3Small
        TextEmbeddingAda002.value -> TextEmbeddingAda002
        else -> null
      }
    }
  }
}

class AzureEmbedder(apiVersion: String, endpoint: String, apiKey: String) : Embedder {
  private val client =
    OpenAI(
      OpenAIConfig(
        host =
          OpenAIHost.azure(
            resourceName = endpoint,
            deploymentId = DEPLOYMENT_ID,
            apiVersion = apiVersion,
          ),
        headers = mapOf("api-key" to apiKey),
        token = apiKey,
      )
    )

  override fun id(): String {
    return "azure"
  }

  override suspend fun embed(input: String, model: String): List<Double> {
    OpenAiModel.tryFromString(model)
      ?: throw apiError(
        "Invalid embedding model",
        "Embedder '${id()}' does not support model '$model'",
      )

    val request = EmbeddingRequest(ModelId(model), listOf(input))

    val response = client.embeddings(request)

    // TODO: Figure out proper error
    if (response.embeddings.isEmpty()) {
      throw internalError()
    }

    // We always get embeddings in a 1:1 manner relative to the input
    // Above check ensures we are always in bounds
    return response.embeddings.map { it.embedding }[0]
  }
}
