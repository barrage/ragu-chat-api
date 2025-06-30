package net.barrage.llmao.test

import io.ktor.util.logging.KtorSimpleLogger
import io.weaviate.client.Config
import io.weaviate.client.WeaviateClient
import io.weaviate.client.v1.data.model.WeaviateObject
import io.weaviate.client.v1.filters.Operator
import io.weaviate.client.v1.filters.WhereFilter
import io.weaviate.client.v1.schema.model.Property
import io.weaviate.client.v1.schema.model.WeaviateClass
import java.util.*
import kotlin.random.Random
import org.testcontainers.weaviate.WeaviateContainer

class TestWeaviate {
  val container: WeaviateContainer =
    WeaviateContainer("cr.weaviate.io/semitechnologies/weaviate:1.25.5")
  val client: WeaviateClient

  private val log = KtorSimpleLogger("WeaviateTestContainer")

  init {
    container.start()
    client = WeaviateClient(Config("http", container.httpHostAddress))
  }

  fun insertTestCollection(
    name: String = "TestClass",
    size: Int = 1536,
    embeddingProvider: String = "azure",
    embeddingModel: String = "text-embedding-ada-002",
    groups: List<String>? = null,
  ) {

    val properties =
      mutableListOf<Property>(
        Property.builder().name("collection_id").dataType(listOf("uuid")).build(),
        Property.builder().name("size").dataType(listOf("int")).build(),
        Property.builder().name("name").description(name).dataType(listOf("text")).build(),
        Property.builder().name("embedding_provider").dataType(listOf("text")).build(),
        Property.builder().name("embedding_model").dataType(listOf("text")).build(),
        Property.builder().name("groups").dataType(listOf("text[]")).build(),
      )

    val newClass =
      WeaviateClass.builder()
        .className(name)
        .description("Test vector collection")
        .properties(properties)
        .build()

    val idVector: List<Float> = List(size) { Random.nextFloat() }
    client.schema().classCreator().withClass(newClass).run()
    val result =
      client
        .data()
        .creator()
        .withClassName(name)
        .withVector(idVector.toTypedArray())
        .withID(UUID(0L, 0L).toString())
        .withProperties(
          mapOf(
            "name" to name,
            "collection_id" to UUID.randomUUID(),
            "size" to size,
            "embedding_provider" to embeddingProvider,
            "embedding_model" to embeddingModel,
            "groups" to groups,
          )
        )
        .run()

    assert(result.error == null)

    log.info("inserted test collection: {}", name)
  }

  fun insertVectors(collection: String, vectors: List<Pair<String, List<Float>>>) {
    val batcher = client.batch().objectsBatcher()

    vectors.forEach { (content, vector) ->
      val properties = HashMap<String, Any>()
      properties["content"] = content

      val obj =
        WeaviateObject.builder()
          .className(collection)
          .properties(properties)
          .vector(vector.toTypedArray())
          .build()

      batcher.withObject(obj)
    }

    val result = batcher.run()

    if (result.hasErrors()) {
      throw RuntimeException("Error inserting vectors: ${result.error}")
    }
  }

  fun deleteVectors(collection: String) {
    client
      .batch()
      .objectsBatchDeleter()
      .withClassName(collection)
      .withWhere(
        WhereFilter.builder()
          .operator(Operator.NotEqual)
          .path("id")
          .valueString(UUID(0L, 0L).toString())
          .build()
      )
      .run()
  }
}
