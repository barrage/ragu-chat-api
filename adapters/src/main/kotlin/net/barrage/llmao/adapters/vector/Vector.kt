package net.barrage.llmao.adapters.vector

import io.ktor.server.config.ApplicationConfig
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.string
import net.barrage.llmao.core.vector.VectorDatabase

fun initializeVectorDatabases(config: ApplicationConfig): ProviderFactory<VectorDatabase> =
  ProviderFactory<VectorDatabase>().apply {
    // Weaviate is always enabled
    register(Weaviate.initialize(config.string("weaviate.scheme"), config.string("weaviate.host")))
  }
