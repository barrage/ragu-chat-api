package net.barrage.llmao.weaviate

import io.ktor.server.plugins.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import java.util.*
import net.barrage.llmao.models.*

class Weaver(config: WeaviateConfig) {
  private val client: WeaviateClient = WeaviateClient(Config(config.scheme, config.host))

  fun query(embeddings: List<Double>, options: VectorQueryOptions): List<String> {
    val fields = Field.builder().name("content").build()

    val results = mutableListOf<String>()

    for (collection in options.collections) {
      val query =
        client
          .graphQL()
          .get()
          .withFields(fields)
          .withClassName(collection)
          .withNearVector(
            NearVectorArgument.builder()
              .vector(embeddings.map { it.toFloat() }.toTypedArray())
              .build()
          )
          .withLimit(options.amountResults)

      val queryResult = query.run()

      if (queryResult.hasErrors()) {
        val errorMessage = queryResult.error.messages.joinToString(", ")
        throw BadRequestException("Failed to query: $errorMessage")
      }

      val result = queryResult.result.data as? Map<*, *>

      if (result == null) {
        println("Cannot cast ${queryResult.result.data} to Map")
        continue
      }

      val data = result["Get"] as? Map<*, *>

      if (data == null) {
        println("Cannot cast ${result["Get"]} to Map")
        continue
      }

      val collectionData = data[collection] as? List<*>

      if (collectionData == null) {
        println("Cannot cast ${collectionData} to List")
        continue
      }

      for (item in collectionData) {
        (item as? Map<*, *>)?.let {
          val content = it["content"] as String
          results.add(content)
        }
      }
    }

    return results
  }
}
