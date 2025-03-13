package net.barrage.llmao.app.vector

import io.ktor.util.logging.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.base.Result
import io.weaviate.client.v1.graphql.model.GraphQLError
import io.weaviate.client.v1.schema.model.Property
import kotlin.properties.Delegates
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.CollectionQuery
import net.barrage.llmao.core.vector.VectorCollectionInfo
import net.barrage.llmao.core.vector.VectorData
import net.barrage.llmao.core.vector.VectorDatabase

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.vector.Weaviate")

class Weaviate(scheme: String, host: String) : VectorDatabase {
  private val client: WeaviateClient = WeaviateClient(Config(scheme, host))

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

    val query = constructQuery(queries)

    val requestResult = client.graphQL().raw().withQuery(query).run()

    if (requestResult.hasErrors()) {
      throw requestError(requestResult.error)
    }

    // Actual result of the query.
    val result = requestResult.result

    if (result.errors != null) {
      // Happens when the collection is empty. We do not want to error on empty collections.
      if (result.errors.find { it.message.startsWith("Cannot query field") } != null) {
        LOG.warn(
          "Invalid field detected, skipping; This could be due to leftover fields during development or the collection being empty."
        )
        LOG.warn("Original error: {}", result.errors)
        return mapOf()
      }
      throw queryError(result.errors)
    } else if (result.data == null) {
      LOG.warn("Weaviate did not return any data; Result: {}", result)
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

        if (content == null) {
          LOG.error("Weaviate query error: Content is null (collection: $collectionName)")
          continue
        }

        results[collectionName]!!.add(VectorData(content, documentId))
      }

      LOG.debug("Successful query in '{}' ({}/{} results)", collectionName, vectorData.size, amount)
    }

    return results
  }

  override fun getCollectionInfo(name: String): VectorCollectionInfo? {
    val clazz =
      handleResponseError { client.schema().classGetter().withClassName(name).run() } ?: return null
    return VectorCollectionInfo.fromWeaviateProperties(clazz.properties)
  }

  private fun <T> handleResponseError(block: () -> Result<T?>): T? {
    val response = block()

    if (response.hasErrors()) {
      LOG.error(
        "Weaviate request error: {}, with status: {}",
        response.error.messages.first().message,
        response.error.statusCode,
      )
      return null
    } else if (response.result == null) {
      LOG.error("Weaviate schema error; Result is null; Response: {}", response)
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
      finalQuery +=
        " Get { ${query.name}(limit: ${query.amount}, nearVector: { vector: [$vector] }) { content } }"
    }
    return "$finalQuery }"
  }

  /** Error in transport to Weaviate. */
  private fun requestError(weaviateError: io.weaviate.client.base.WeaviateError): AppError {
    LOG.error("Weaviate GraphQL error: $weaviateError")
    return AppError.api(ErrorReason.VectorDatabase, "Weaviate GraphQL error: $weaviateError")
  }

  /** Internal error when remapping GraphQL data. We do not expose our n00bery to clients. */
  private fun mappingError(error: String): AppError {
    LOG.error("Weaviate mapping error: $error")
    return AppError.internal()
  }

  private fun queryError(errors: Array<GraphQLError>): AppError {
    LOG.error("Weaviate query error")

    var message = ""

    for (error in errors) {
      LOG.error(" - ${error.message}")
      message += "${error.message}\n"
    }

    return AppError.api(ErrorReason.VectorDatabase, message)
  }
}

private fun VectorCollectionInfo.Companion.fromWeaviateProperties(
  properties: List<Property>
): VectorCollectionInfo {
  lateinit var id: KUUID
  lateinit var name: String
  var size by Delegates.notNull<Int>()
  lateinit var embeddingModel: String
  lateinit var embeddingProvider: String
  val vectorProvider = "weaviate"

  for (property in properties) {
    when (property.name) {
      "collection_id" -> id = KUUID.fromString(property.description)
      "size" -> size = property.description.toInt()
      "name" -> name = property.description
      "embedding_model" -> embeddingModel = property.description
      "embedding_provider" -> embeddingProvider = property.description
    }
  }

  return VectorCollectionInfo(
    collectionId = id,
    name = name,
    size = size,
    embeddingModel = embeddingModel,
    embeddingProvider = embeddingProvider,
    vectorProvider = vectorProvider,
  )
}
