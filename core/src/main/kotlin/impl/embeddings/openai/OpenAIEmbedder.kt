package net.barrage.llmao.core.impl.embeddings.openai

import com.aallam.openai.api.embedding.EmbeddingRequest
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIHost
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.embedding.Embeddings
import net.barrage.llmao.core.token.TokenUsageAmount

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
