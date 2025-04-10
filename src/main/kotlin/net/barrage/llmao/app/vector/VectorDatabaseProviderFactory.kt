package net.barrage.llmao.app.vector

import io.ktor.server.config.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.vector.VectorDatabase

class VectorDatabaseProviderFactory(config: ApplicationConfig) : ProviderFactory<VectorDatabase>() {
  init {
    with(initWeaviate(config)) { providers[id()] = this }
  }

  private fun initWeaviate(config: ApplicationConfig): Weaviate {
    return Weaviate(
      config.property("weaviate.scheme").getString(),
      config.property("weaviate.host").getString(),
    )
  }
}
