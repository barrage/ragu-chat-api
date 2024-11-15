package net.barrage.llmao.app.vector

import io.ktor.server.application.*
import io.ktor.server.config.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class VectorDatabaseProviderFactory(config: ApplicationConfig) : ProviderFactory<VectorDatabase>() {
  private val weaviate: Weaviate

  init {
    weaviate = initWeaviate(config)
  }

  override fun getProvider(providerId: String): VectorDatabase {
    return when (providerId) {
      weaviate.id() -> weaviate
      else ->
        throw AppError.api(
          ErrorReason.InvalidProvider,
          "Unsupported vector database provider '$providerId'",
        )
    }
  }

  override fun listProviders(): List<String> {
    return listOf(weaviate.id())
  }

  private fun initWeaviate(config: ApplicationConfig): Weaviate {
    return Weaviate(
      config.property("weaviate.scheme").getString(),
      config.property("weaviate.host").getString(),
    )
  }
}
