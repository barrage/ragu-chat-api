package embeddings.openai

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.embedding.Embeddings
import net.barrage.llmao.core.token.TokenUsageAmount

private val log = KtorSimpleLogger("adapters.embeddings.OpenAIEmbedder")

/**
 * @param endpoint The Azure endpoint to use. Should include the resource identifier.
 * @param apiKey The Azure API key to use.
 */
class OpenAIEmbedder(
  endpoint: String,
  apiKey: String,

  /** Maps model codes to their embedding sizes. */
  private val models: Map<String, Int>,
) : Embedder {
  private val client: OpenAI = OpenAI(token = apiKey, host = OpenAIHost(endpoint))

  companion object {
    fun initialize(endpoint: String, apiKey: String, models: Map<String, Int>): OpenAIEmbedder {
      //      val endpoint = config.string("embeddings.openai.endpoint")
      //      val apiKey = config.string("embeddings.openai.apiKey")
      //      val models =
      //        config
      //          .configList("embeddings.openai.models")
      //          .associateBy { it.string("model") }
      //          .mapValues { it.value.int("vectorSize") }

      if (models.isEmpty()) {
        throw AppError.internal(
          """Invalid openai configuration; Check your `embeddings.openai.models` config.
           | At least one model must be specified.
           | If you do not intend to use OpenAI, set the `ktor.features.embeddings.openai` flag to `false`.
           |"""
            .trimMargin()
        )
      }

      log.info("Initializing OpenAI embeddings with models: {}", models.keys.joinToString(", "))

      return OpenAIEmbedder(endpoint, apiKey, models)
    }
  }

  override fun id(): String = "openai"

  override suspend fun supportsModel(model: String): Boolean = models.containsKey(model)

  override suspend fun embed(input: String, model: String): Embeddings {
    val request = EmbeddingRequest(ModelId(model), listOf(input))

    val response = client.embeddings(request)

    // TODO: Figure out proper error
    if (response.embeddings.isEmpty()) {
      throw AppError.internal()
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
    return models[model]
      ?: throw AppError.api(ErrorReason.InvalidParameter, "Model $model not found")
  }
}
