package net.barrage.llmao.app.embeddings.openai

internal enum class OpenAIEmbeddingModel(val model: String, val vectorSize: Int) {
  TextEmbedding3Large("text-embedding-3-large", 3072),
  TextEmbedding3Small("text-embedding-3-small", 1536),
  TextEmbeddingAda002("text-embedding-ada-002", 1536);

  companion object {
    fun tryFromString(value: String): OpenAIEmbeddingModel? {
      return when (value) {
        TextEmbedding3Large.model -> TextEmbedding3Large
        TextEmbedding3Small.model -> TextEmbedding3Small
        TextEmbeddingAda002.model -> TextEmbeddingAda002
        else -> null
      }
    }
  }
}
