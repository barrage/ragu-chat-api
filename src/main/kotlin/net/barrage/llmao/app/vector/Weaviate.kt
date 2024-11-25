package net.barrage.llmao.app.vector

import io.ktor.util.logging.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.graphql.model.GraphQLError
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.vector.Weaviate")

// TODO: ADD WEAVIATE DATA CLASSES
class Weaviate(scheme: String, host: String) : VectorDatabase {
  private val client: WeaviateClient = WeaviateClient(Config(scheme, host))

  override fun id(): String {
    return "weaviate"
  }

  /**
   * Query Weaviate based on `searchVector` and return `amount` most similar results.
   *
   * This method does not throw in case the query returns no results. Only internal errors and
   * errors obtained from the GraphQL API are thrown.
   */
  override fun query(searchVector: List<Double>, collection: String, amount: Int): List<String> {
    val fields = Field.builder().name("content").build()

    val results = mutableListOf<String>()

    val query =
      client
        .graphQL()
        .get()
        .withFields(fields)
        .withClassName(collection)
        .withNearVector(
          NearVectorArgument.builder()
            .vector(searchVector.map { it.toFloat() }.toTypedArray())
            .build()
        )
        .withLimit(amount)

    val requestResult = query.run()

    if (requestResult.hasErrors()) {
      throw requestError(requestResult.error)
    }

    // Actual result of the query.
    val result = requestResult.result

    if (result.errors != null) {
      // Happens when the collection is empty. We do not want to error on empty collections.
      if (result.errors.find { it.message.startsWith("Cannot query field \"content\"") } != null) {
        LOG.warn("Empty collection detected, skipping; Original error: {}", result.errors)
        return listOf()
      }
      throw queryError(result.errors)
    } else if (result.data == null) {
      LOG.warn("Weaviate did not return any data; Result: {}", result)
      return listOf()
    }

    val data =
      requestResult.result.data as? Map<*, *>
        ?: throw mappingError("Cannot cast ${requestResult.result.data} to Map")

    val mappedData =
      data["Get"] as? Map<*, *> ?: throw mappingError("Cannot cast ${data["Get"]} to Map")

    val collectionData =
      mappedData[collection] as? List<*>
        ?: throw mappingError("Cannot cast ${mappedData[collection]} to List, skipping")

    for (item in collectionData) {
      (item as? Map<*, *>)?.let {
        val content = it["content"] as String
        results.add(content)
      }
    }

    LOG.debug("Successful query in '$collection' (${results.size}/$amount results)")

    return results
  }

  override fun validateCollection(name: String, vectorSize: Int): Boolean {
    val response = client.schema().classGetter().withClassName(name).run()

    if (response.hasErrors()) {
      LOG.error(
        "Weaviate request error: {}, with status: {}",
        response.error.messages.first().message,
        response.error.statusCode,
      )
      return false
    } else if (response.result == null) {
      LOG.error("Weaviate schema error: Collection '{}' not found", name)
      return false
    }

    val properties = response.result.properties
    val size = properties.first { it.name == "size" }.description.toInt()

    if (size != vectorSize) {
      LOG.error(
        "Weaviate vector size mismatch: Collection '$name' has size $size, expected $vectorSize"
      )
      return false
    }

    return true
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
