package net.barrage.llmao.core.embedding

import net.barrage.llmao.core.Identity
import net.barrage.llmao.core.token.TokenUsageAmount

interface Embedder : Identity {
    /** Return `true` if the embedder supports the given model. */
    suspend fun supportsModel(model: String): Boolean

    /** Embed the given input string using the provided embedding model. */
    suspend fun embed(input: String, model: String): Embeddings

    /** Return the size of the vector for the given model. */
    suspend fun vectorSize(model: String): Int
}

data class Embeddings(val embeddings: List<Double>, val usage: TokenUsageAmount? = null)
