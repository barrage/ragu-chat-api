package net.barrage.llmao.app.vector

import io.ktor.server.application.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.apiError

class VectorDatabaseProviderFactory(env: ApplicationEnvironment) :
  ProviderFactory<VectorDatabase>() {
  private val weaviate: Weaveiate

  init {
    weaviate = initWeaviate(env)
  }

  override fun getProvider(providerId: String): VectorDatabase {
    return when (providerId) {
      weaviate.id() -> weaviate
      else -> throw apiError("Provider", "Unsupported vector database provider '$providerId'")
    }
  }

  private fun initWeaviate(env: ApplicationEnvironment): Weaveiate {
    return Weaveiate(
      env.config.property("weaviate.scheme").getString(),
      env.config.property("weaviate.host").getString(),
    )
  }
}
