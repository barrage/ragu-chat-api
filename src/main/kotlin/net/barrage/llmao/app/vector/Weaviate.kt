package net.barrage.llmao.app.vector

import io.ktor.util.logging.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.WeaviateError

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
      throw WeaviateError.requestError(queryResult.error)
    }

    val result = queryResult.result

    if (result == null) {
      throw WeaviateError.queryError("No result")
    } else if (result.errors != null) {
      throw WeaviateError.queryError(result.errors)
    } else if (result.data == null) {
      throw WeaviateError.queryError("No data")
    }

    val data =
      queryResult.result.data as? Map<*, *>
        ?: throw WeaviateError.mappingError("Cannot cast ${queryResult.result.data} to Map")

    val mappedData =
      data["Get"] as? Map<*, *>
        ?: throw WeaviateError.mappingError("Cannot cast ${data["Get"]} to Map")

    val collectionData =
      mappedData[collection] as? List<*>
        ?: throw WeaviateError.mappingError(
          "Cannot cast ${mappedData[collection]} to List, skipping"
        )

    for (item in collectionData) {
      (item as? Map<*, *>)?.let {
        val content = it["content"] as String
        results.add(content)
      }
    }

    LOG.debug("Successful query in '$collection' (target ${results.size}/$amount results)")

    return results
  }

  override fun validateCollection(name: String, vectorSize: Int): Boolean {
    val response = client.schema().classGetter().withClassName(name).run()

    if (response.hasErrors()) {
      WeaviateError.requestErrorValidateCollection(response.error)
      return false
    } else if (response.result == null) {
      WeaviateError.schemaError("Collection '$name' does not exist")
      return false
    }

    val properties = response.result.properties
    val size = properties.first { it.name == "size" }.description.toInt()

    if (size != vectorSize) {
      WeaviateError.vectorSizeError("Collection '$name' has size $size, expected $vectorSize")
      return false
    }

    return true
  }
}
