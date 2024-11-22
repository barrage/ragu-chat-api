package net.barrage.llmao.app.embeddings

import io.ktor.server.config.*
import net.barrage.llmao.app.embeddings.openai.AzureEmbedder
import net.barrage.llmao.app.embeddings.openai.OpenAIEmbedder
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class EmbeddingProviderFactory(config: ApplicationConfig) : ProviderFactory<Embedder>() {
  private val openai: OpenAIEmbedder
  private val azure: AzureEmbedder
  private val fembed: FastEmbedder

  init {
    openai = initOpenAIEmbedder(config)
    azure = initAzureEmbedder(config)
    fembed = initFastEmbedder(config)
  }

  override fun getProvider(providerId: String): Embedder {
    return when (providerId) {
      openai.id() -> openai
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
    return listOf(openai.id(), azure.id(), fembed.id())
  }

  private fun initOpenAIEmbedder(config: ApplicationConfig): OpenAIEmbedder {
    val endpoint = config.property("embeddings.openai.endpoint").getString()
    val apiKey = config.property("embeddings.openai.apiKey").getString()

    return OpenAIEmbedder(endpoint, apiKey)
  }

  private fun initAzureEmbedder(config: ApplicationConfig): AzureEmbedder {
    val endpoint = config.property("embeddings.azure.endpoint").getString()
    val deployment = config.property("embeddings.azure.deployment").getString()
    val apiVersion = config.property("embeddings.azure.apiVersion").getString()
    val apiKey = config.property("embeddings.azure.apiKey").getString()

    return AzureEmbedder(endpoint, deployment, apiVersion, apiKey)
  }

  private fun initFastEmbedder(config: ApplicationConfig): FastEmbedder {
    val endpoint = config.property("embeddings.fembed.endpoint").getString()

    return FastEmbedder(endpoint)
  }
}
