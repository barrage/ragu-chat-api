package net.barrage.llmao.app.embeddings

import io.ktor.server.application.*
import io.ktor.server.config.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class EmbeddingProviderFactory(config: ApplicationConfig) : ProviderFactory<Embedder>() {
  private val azure: AzureEmbedder
  private val fembed: FastEmbedder

  init {
    azure = initAzureEmbedder(config)
    fembed = initFastEmbedder(config)
  }

  override fun getProvider(providerId: String): Embedder {
    return when (providerId) {
      azure.id() -> azure
      fembed.id() -> fembed
      else ->
        throw AppError.api(
          ErrorReason.InvalidProvider,
          "Unsupported embedding provider '$providerId'",
        )
    }
  }

  override fun listProviders(): List<String> {
    return listOf(azure.id(), fembed.id())
  }

  private fun initAzureEmbedder(config: ApplicationConfig): AzureEmbedder {
    val apiVersion = config.property("embeddings.azure.apiVersion").getString()
    val endpoint = config.property("embeddings.azure.endpoint").getString()
    val apiKey = config.property("embeddings.azure.apiKey").getString()

    return AzureEmbedder(apiVersion, endpoint, apiKey)
  }

  private fun initFastEmbedder(config: ApplicationConfig): FastEmbedder {
    val endpoint = config.property("embeddings.fembed.endpoint").getString()

    return FastEmbedder(endpoint)
  }
}
