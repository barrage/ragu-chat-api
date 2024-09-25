package net.barrage.llmao.weaviate.collections

import io.ktor.server.config.*
import net.barrage.llmao.models.VectorQueryOptions

object Documentation {
  lateinit var vectorQueryOptions: VectorQueryOptions

  fun init(config: ApplicationConfig) {
    vectorQueryOptions =
      VectorQueryOptions(
        collection = config.property("weaviate.documentation.collection").getString(),
        fields = config.property("weaviate.documentation.fields").getString(),
        nResults = config.property("weaviate.documentation.nResults").getString().toInt(),
        where = config.propertyOrNull("weaviate.documentation.where")?.getString(),
        distanceFilter =
          config.propertyOrNull("weaviate.documentation.distanceFilter")?.getString()?.toDouble(),
      )
  }
}
