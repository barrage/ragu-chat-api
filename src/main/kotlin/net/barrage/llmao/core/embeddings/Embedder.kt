package net.barrage.llmao.core.embeddings

interface Embedder {
  fun id(): String

  /** Embed the given input string using the provided embedding model. */
  suspend fun embed(input: String, model: String): List<Double>
}
