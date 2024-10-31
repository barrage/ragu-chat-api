package net.barrage.llmao.app.vector

import io.ktor.server.plugins.*
import io.ktor.util.logging.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.AppError

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.vector.Weaviate")

class Weaviate(scheme: String, host: String) : VectorDatabase {
  private val client: WeaviateClient = WeaviateClient(Config(scheme, host))

  override fun id(): String {
    return "weaviate"
  }

  @Suppress("NAME_SHADOWING")
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

    val queryResult = query.run()

    if (queryResult.hasErrors()) {
      val errorMessage = queryResult.error.messages.joinToString(", ")
      throw BadRequestException("Failed to query: $errorMessage")
    }

    // TODO: Check result.error and act accordingly
    val result = queryResult.result.data as? Map<*, *>

    if (result == null) {
      LOG.error("Cannot cast ${queryResult.result.data} to Map")
      throw AppError.internal()
    }

    val data = result["Get"] as? Map<*, *>

    if (data == null) {
      LOG.error("Cannot cast ${result["Get"]} to Map")
      throw AppError.internal()
    }

    val collectionData = data[collection] as? List<*>

    if (collectionData == null) {
      LOG.error("Cannot cast $collectionData to List, skipping")
      throw AppError.internal()
    }

    LOG.debug("Successful query in '$collection' (target $amount results)")

    for (item in collectionData) {
      (item as? Map<*, *>)?.let {
        val content = it["content"] as String
        results.add(content)
      }
    }

    return results
  }

  override fun validateCollection(name: String): Boolean {
    try {
      val result = client.schema().exists().withClassName(name).run()
      return result.result ?: false
    } catch (e: Exception) {
      LOG.error("Error validating collection '$name': ${e.message}")
      return false
    }
  }
}
