package net.barrage.llmao.app.vector

import io.ktor.server.plugins.*
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.graphql.query.argument.NearVectorArgument
import io.weaviate.client.v1.graphql.query.fields.Field
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.utils.Logger

class Weaveiate(scheme: String, host: String) : VectorDatabase {
  private val client: WeaviateClient = WeaviateClient(Config(scheme, host))

  override fun id(): String {
    return "weaviate"
  }

  override fun query(searchVector: List<Double>, options: List<Pair<String, Int>>): List<String> {
    val fields = Field.builder().name("content").build()

    val results = mutableListOf<String>()

    for ((collectionName, amount) in options) {
      val collection = toWeaviateClassName(collectionName)

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
        println("Cannot cast $collectionData to List")
        continue
      }

      Logger.debug("Query in '$collection' ($amount results):")
      Logger.debug("$collectionData")

      for (item in collectionData) {
        (item as? Map<*, *>)?.let {
          val content = it["content"] as String
          results.add(content)
        }
      }
    }

    return results
  }

  override fun validateCollection(name: String): Boolean {
    println(toWeaviateClassName(name))
    val result = client.schema().exists().withClassName(toWeaviateClassName(name)).run()
    return result.result
  }

  /** Necessary because weaviate capitalizes all class names. */
  private fun toWeaviateClassName(name: String): String {
    return name.replaceFirstChar { it.uppercaseChar() }
  }
}
