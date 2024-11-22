package net.barrage.llmao.app.embeddings.openai

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/**
 * @param endpoint The Azure endpoint to use. Should include the resource identifier.
 * @param apiKey The Azure API key to use.
 */
class OpenAIEmbedder(endpoint: String, apiKey: String) : Embedder {
  private val client: OpenAI = OpenAI(token = apiKey, host = OpenAIHost(endpoint))

  override fun id(): String {
    return "openai"
  }

  override suspend fun supportsModel(model: String): Boolean {
    return OpenAIEmbeddingModel.tryFromString(model) != null
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
    return OpenAIEmbeddingModel.tryFromString(model)?.vectorSize
      ?: throw AppError.api(ErrorReason.InvalidParameter, "Model $model not found")
  }
}
