package net.barrage.llmao.core.vector

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KUUID

interface VectorDatabase {
  fun id(): String

  /** Perform semantic similarity search using the given `queries`. */
  fun query(queries: List<CollectionQuery>): Map<String, List<VectorData>>

  /** Get collection info directly from a vector database. */
  fun getCollectionInfo(name: String): VectorCollectionInfo?
}

/** Data class representing the payload associated with a vector. */
data class VectorData(
  /** The original document (or chunk) content. */
  val content: String,

  /** The document ID in `chonkit`. */
  val documentId: String?,
)

@Serializable
data class VectorCollectionInfo(
  val collectionId: KUUID,

  /** Collection name. */
  val name: String,

  /** Embedding vector size. */
  val size: Int,

  /** Embedding model used for the collection. */
  val embeddingModel: String,

  /** Embedding provider for the model. */
  val embeddingProvider: String,

  /** Underlying vector storage provider. */
  val vectorProvider: String,
)

/**
 * Used by vector databases to obtain the most similar results from a collection. The `vector`
 * length must always be the same as the collection's vector size.
 */
data class CollectionQuery(val name: String, val amount: Int, val vector: List<Double>)
