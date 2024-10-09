package net.barrage.llmao.app.vector

import io.ktor.server.application.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.vector.VectorDatabase
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class VectorDatabaseProviderFactory(env: ApplicationEnvironment) :
  ProviderFactory<VectorDatabase>() {
  private val weaviate: Weaveiate

  init {
    weaviate = initWeaviate(env)
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

  private fun initWeaviate(env: ApplicationEnvironment): Weaveiate {
    return Weaveiate(
      env.config.property("weaviate.scheme").getString(),
      env.config.property("weaviate.host").getString(),
    )
  }
}
