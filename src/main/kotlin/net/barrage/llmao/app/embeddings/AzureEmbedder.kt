package net.barrage.llmao.app.embeddings

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.aallam.openai.client.OpenAIHost
import io.ktor.server.auth.*
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/**
 * @param endpoint The Azure endpoint to use. Should include the resource identifier.
 * @param deployment The Azure deployment to use. TODO: Figure out if this is always 1:1 with
 *   models.
 * @param apiVersion The Azure API version to use.
 * @param apiKey The Azure API key to use.
 */
class AzureEmbedder(endpoint: String, deployment: String, apiVersion: String, apiKey: String) :
  Embedder {
  private val client: OpenAI

  init {
    val host =
      OpenAIHost(
        baseUrl = "$endpoint/$deployment/",
        queryParams = mapOf("api-version" to apiVersion),
      )

    client = OpenAI(OpenAIConfig(host = host, headers = mapOf("api-key" to apiKey), token = apiKey))
  }

  override fun id(): String {
    return "azure"
  }

  override suspend fun supportsModel(model: String): Boolean {
    return OpenAiModel.tryFromString(model) != null
  }

  override suspend fun embed(input: String, model: String): List<Double> {
    val request = EmbeddingRequest(ModelId(model), listOf(input))

    val response = client.embeddings(request)

    // TODO: Figure out proper error
    if (response.embeddings.isEmpty()) {
      throw AppError.internal()
    }

    // We always get embeddings in a 1:1 manner relative to the input
    // Above check ensures we are always in bounds
    return response.embeddings.map { it.embedding }[0]
  }

  override suspend fun vectorSize(model: String): Int {
    return OpenAiModel.tryFromString(model)?.vectorSize
      ?: throw AppError.api(ErrorReason.InvalidParameter, "Model $model not found")
  }
}

private enum class OpenAiModel(val model: String, val vectorSize: Int) {
  TextEmbedding3Large("text-embedding-3-large", 3072),
  TextEmbedding3Small("text-embedding-3-small", 1536),
  TextEmbeddingAda002("text-embedding-ada-002", 1536);

  companion object {
    fun tryFromString(value: String): OpenAiModel? {
      return when (value) {
        TextEmbedding3Large.model -> TextEmbedding3Large
        TextEmbedding3Small.model -> TextEmbedding3Small
        TextEmbeddingAda002.model -> TextEmbeddingAda002
        else -> null
      }
    }
  }
}
