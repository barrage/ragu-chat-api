package net.barrage.llmao.core.vector

interface VectorDatabase {
  fun id(): String

  /**
   * Perform semantic similarity search in the given `collection`, returning `amount` most similar
   * results.
   */
  fun query(searchVector: List<Double>, collection: String, amount: Int): List<String>

  /**
   * Used by services to reason about the existence of collections. Returns `true` if the collection
   * exists and can be used by an agent, `false` otherwise.
   */
  fun validateCollection(name: String): Boolean
}
