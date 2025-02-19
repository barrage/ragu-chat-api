package net.barrage.llmao.core.embeddings

interface Embedder {
  fun id(): String

  /** Return `true` if the embedder supports the given model. */
  suspend fun supportsModel(model: String): Boolean

  /** Embed the given input string using the provided embedding model. */
  suspend fun embed(input: String, model: String): Embeddings

  /** Return the size of the vector for the given model. */
  suspend fun vectorSize(model: String): Int
}
