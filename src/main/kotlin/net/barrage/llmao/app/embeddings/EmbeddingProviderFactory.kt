package net.barrage.llmao.app.embeddings

import io.ktor.server.config.*
import net.barrage.llmao.app.embeddings.openai.AzureEmbedder
import net.barrage.llmao.app.embeddings.openai.OpenAIEmbedder
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embeddings.Embedder
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

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
    val endpoint = config.string("embeddings.openai.endpoint")
    val apiKey = config.string("embeddings.openai.apiKey")

    return OpenAIEmbedder(endpoint, apiKey)
  }

  private fun initAzureEmbedder(config: ApplicationConfig): AzureEmbedder {
    val endpoint = config.string("embeddings.azure.endpoint")
    val deployment = config.string("embeddings.azure.deployment")
    val apiVersion = config.string("embeddings.azure.apiVersion")
    val apiKey = config.string("embeddings.azure.apiKey")

    return AzureEmbedder(endpoint, deployment, apiVersion, apiKey)
  }

  private fun initFastEmbedder(config: ApplicationConfig): FastEmbedder {
    val endpoint = config.string("embeddings.fembed.endpoint")

    return FastEmbedder(endpoint)
  }
}
