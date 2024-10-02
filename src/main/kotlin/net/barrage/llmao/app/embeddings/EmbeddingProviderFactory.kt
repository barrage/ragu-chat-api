package net.barrage.llmao.app.embeddings

import io.ktor.server.application.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.apiError

class EmbeddingProviderFactory(env: ApplicationEnvironment) : ProviderFactory<Embedder>() {
  private val azure: AzureEmbedder
  private val fembed: FastEmbedder

  init {
    azure = initAzureEmbedder(env)
    fembed = initFastEmbedder(env)
  }

  override fun getProvider(providerId: String): Embedder {
    return when (providerId) {
      azure.id() -> azure
      fembed.id() -> fembed
      else -> throw apiError("Provider", "Unsupported embedding provider '$providerId'")
    }
  }

  private fun initAzureEmbedder(env: ApplicationEnvironment): AzureEmbedder {
    val apiVersion = env.config.property("embeddings.azure.apiVersion").getString()
    val endpoint = env.config.property("embeddings.azure.endpoint").getString()
    val apiKey = env.config.property("embeddings.azure.apiKey").getString()

    return AzureEmbedder(apiVersion, endpoint, apiKey)
  }

  private fun initFastEmbedder(env: ApplicationEnvironment): FastEmbedder {
    val endpoint = env.config.property("embeddings.fembed.endpoint").getString()

    return FastEmbedder(endpoint)
  }
}
