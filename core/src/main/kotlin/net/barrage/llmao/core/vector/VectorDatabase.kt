package net.barrage.llmao.core.vector

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.Identity
import net.barrage.llmao.core.types.KUUID

interface VectorDatabase : Identity {
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

/** Has to follow the schema for Ragu vector collections. */
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

  /** Which groups can access this collection. */
  val groups: List<String>? = null,
)

/**
 * Used by vector databases to obtain the most similar results from a collection. The `vector`
 * length must always be the same as the collection's vector size.
 */
data class CollectionQuery(
  /** The name of the collection to query. */
  val name: String,

  /** The maximum amount of vectors to return. */
  val amount: Int,

  /** The search vector. */
  val vector: List<Double>,

  /** Filter any results above this distance. */
  val maxDistance: Double?,
)
