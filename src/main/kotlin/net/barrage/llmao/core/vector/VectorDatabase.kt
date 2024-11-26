package net.barrage.llmao.core.vector

interface VectorDatabase {
  fun id(): String

  /**
   * Perform semantic similarity search in the given `collections`, returning `amount` most similar
   * results from each based on the given `searchVector`.
   */
  fun query(
    searchVector: List<Double>,
    collections: List<Pair<String, Int>>,
  ): Map<String, List<VectorData>>

  /**
   * Used by services to reason about the existence of collections. Returns `true` if the collection
   * exists and can be used by an agent, `false` otherwise.
   */
  fun validateCollection(name: String, vectorSize: Int): Boolean
}

/** Data class representing the payload associated with a vector. */
data class VectorData(
  /** The original document (or chunk) content. */
  val content: String,

  /** The document ID in `chonkit`. */
  val documentId: String?,
)
