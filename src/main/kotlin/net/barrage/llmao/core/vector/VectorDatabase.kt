package net.barrage.llmao.core.vector

interface VectorDatabase {
  fun id(): String

  /**
   * Perform semantic similarity search in the given `collections`, returning `amount` most similar
   * results from each.
   */
  fun query(searchVector: List<Double>, options: List<Pair<String, Int>>): List<String>
}