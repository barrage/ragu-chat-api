package net.barrage.llmao.core.embeddings

data class Embeddings(
  val embeddings: List<Double>,
  val tokensUsed: Int? = null,
)
