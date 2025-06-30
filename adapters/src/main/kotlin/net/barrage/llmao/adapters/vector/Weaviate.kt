package net.barrage.llmao.adapters.vector

import io.ktor.util.logging.KtorSimpleLogger
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.base.Result
import io.weaviate.client.base.WeaviateError
import io.weaviate.client.v1.graphql.model.GraphQLError
import java.util.*
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorCollectionInfo
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.core.vector.VectorDatabase

internal val LOG = KtorSimpleLogger("adapters.vector.Weaviate")

class Weaviate(scheme: String, host: String) : VectorDatabase {
  private val client: WeaviateClient = WeaviateClient(Config(scheme, host))

  companion object {
    fun initialize(scheme: String, host: String): Weaviate {
      return Weaviate(scheme, host)
    }
  }

  override fun id(): String {
    return "weaviate"
  }

  /**
   * Query Weaviate based on the provided queries.
   *
   * This method does not throw in case the generated query returns no results. Only internal errors
   * and errors obtained from the GraphQL API are thrown.
   */
  override fun query(queries: List<CollectionQuery>): Map<String, List<VectorData>> {
    if (queries.isEmpty()) {
      return mapOf()
    }

    LOG.debug("executing queries in: {}", queries.joinToString(", ") { it.name })

    val requestResult = client.graphQL().raw().withQuery(constructQuery(queries)).run()

    if (requestResult.hasErrors()) {
      throw requestError(requestResult.error)
    }

    // Actual result of the query.
    val result = requestResult.result

    if (result.errors != null) {
      // Happens when the collection is empty. We do not want to error on empty collections.
      if (result.errors.find { it.message.startsWith("Cannot query field") } != null) {
        LOG.warn(
          "invalid field detected, skipping; This could be due to leftover fields during development or the collection being empty."
        )
        LOG.warn("Original error: {}", result.errors)
        return mapOf()
      }
      throw queryError(result.errors)
    } else if (result.data == null) {
      LOG.warn("no data in query; result: {}", result)
      return mapOf()
    }

    val results = mutableMapOf<String, MutableList<VectorData>>()

    val data =
      requestResult.result.data as? Map<*, *>
        ?: throw mappingError("Cannot cast ${requestResult.result.data} to Map")

    // Get { collectionName: [ { content: "..." }, { content: "..." } ] }
    val mappedData =
      data["Get"] as? Map<*, *> ?: throw mappingError("Cannot cast ${data["Get"]} to Map")

    for (collectionQuery in queries) {
      val collectionName = collectionQuery.name
      val amount = collectionQuery.amount

      val vectorData = mappedData[collectionName] as? List<*> ?: continue // No vectors as List<*>

      results[collectionName] = mutableListOf()

      for (payload in vectorData) {
        val properties = payload as Map<*, *>

        val content = properties["content"] as String?
        val documentId = properties["document_id"] as String?
        val additional = properties["_additional"] as Map<*, *>

        val distance = additional["distance"] as Double?

        if (
          distance != null &&
            collectionQuery.maxDistance != null &&
            distance > collectionQuery.maxDistance!!
        ) {
          LOG.warn("skipping result with distance $distance > ${collectionQuery.maxDistance}")
          continue
        }

        content?.let { results[collectionName]!!.add(VectorData(it, documentId)) }
          ?: LOG.error(
            "query error: content is null (collection: $collectionName, payload: $payload)"
          )
      }

      LOG.debug(
        "successful query in '{}' ({}/{} results)",
        collectionName,
        results[collectionName]!!.size,
        amount,
      )
    }

    return results
  }

  override fun getCollectionInfo(name: String): VectorCollectionInfo? {
    // Validate the collection exists
    val clazz =
      handleResponseError { client.schema().classGetter().withClassName(name).run() } ?: return null

    // Get identity vector which is always nil UUID
    // and contains the collection info
    val classProperties =
      client
        .data()
        .objectsGetter()
        .withClassName(clazz.className)
        .withID(UUID(0L, 0L).toString())
        .withLimit(1)
        .run()

    if (classProperties.result == null) {
      LOG.warn("collection info error, result is null; Response: {}", classProperties)
      return null
    }

    if (classProperties.result.isEmpty()) {
      LOG.warn("collection info does not contain identity vector; Response: {}", classProperties)
      return null
    }

    val properties = classProperties.result.first().properties

    if (properties == null) {
      LOG.warn("collection info does not contain properties; Response: {}", classProperties)
      return null
    }

    return VectorCollectionInfo.fromWeaviateProperties(properties)
  }

  private fun <T> handleResponseError(block: () -> Result<T?>): T? {
    val response = block()

    if (response.hasErrors()) {
      LOG.error(
        "request error: {}, with status: {}",
        response.error.messages.first().message,
        response.error.statusCode,
      )
      return null
    } else if (response.result == null) {
      LOG.error("schema error; Result is null; Response: {}", response)
      return null
    }

    return response.result
  }

  /**
   * Construct a raw GraphQL query so we can obtain the results from each collection with a single
   * request.
   */
  private fun constructQuery(queries: List<CollectionQuery>): String {
    var finalQuery = "query { "
    for (query in queries) {
      val vector = query.vector.joinToString(",")
      // Skip nil ID (identity vector) in the query
      finalQuery +=
        """Get { ${query.name}
            | (
            | where: { operator: NotEqual, path: ["id"], valueText: "${UUID(0L, 0L)}" }
            | limit: ${query.amount},
            | nearVector: { vector: [$vector] }
            | )
            | { content _additional { distance } } }"""
          .trimMargin()
    }
    return "$finalQuery }"
  }

  /** Error in transport to Weaviate. */
  private fun requestError(weaviateError: WeaviateError): AppError {
    LOG.error("weaviate GraphQL error: $weaviateError")
    return AppError.internal()
  }

  /** Internal error when remapping GraphQL data. We do not expose our n00bery to clients. */
  private fun mappingError(error: String): AppError {
    LOG.error("weaviate mapping error: $error")
    return AppError.internal()
  }

  private fun queryError(errors: Array<GraphQLError>): AppError {
    for (error in errors) {
      LOG.error("weaviate query error: ${error.message}")
    }

    return AppError.internal()
  }
}

private fun VectorCollectionInfo.Companion.fromWeaviateProperties(
  properties: Map<String, Any>
): VectorCollectionInfo {
  val collectionId =
    properties["collection_id"]?.let { KUUID.fromString(it as String) }
      ?: throw AppError.internal("missing collection property: collection_id")

  val name =
    properties["name"]?.let { it as String }
      ?: throw AppError.internal("missing collection property: name")

  // Even though tests insert a size as an int, it is returned as a double.
  // Further investigation necessary
  val size =
    properties["size"] as? Int
      ?: (properties["size"] as? Double)?.toInt()
      ?: throw AppError.internal("missing collection property: size")

  val embeddingModel =
    properties["embedding_model"]?.let { it as String }
      ?: throw AppError.internal("missing collection property: embedding_model")

  val embeddingProvider =
    properties["embedding_provider"]?.let { it as String }
      ?: throw AppError.internal("missing collection property: embedding_provider")

  val groups = properties["groups"]?.let { it as? List<*> }?.let { it.map { it as String } }

  return VectorCollectionInfo(
    collectionId = collectionId,
    name = name,
    size = size,
    embeddingModel = embeddingModel,
    embeddingProvider = embeddingProvider,
    vectorProvider = "weaviate",
    groups = groups,
  )
}
