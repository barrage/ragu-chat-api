package net.barrage.llmao.app.embeddings

import io.ktor.server.config.*
import net.barrage.llmao.app.embeddings.openai.AzureEmbedder
import net.barrage.llmao.app.embeddings.openai.OpenAIEmbedder
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.string

class EmbeddingProviderFactory(config: ApplicationConfig) : ProviderFactory<Embedder>() {
  init {
    if (config.tryGetString("ktor.features.embeddings.openai").toBoolean()) {
      providers["openai"] = initOpenAIEmbedder(config)
    }
    if (config.tryGetString("ktor.features.embeddings.azure").toBoolean()) {
      providers["azure"] = initAzureEmbedder(config)
    }
    if (config.tryGetString("ktor.features.embeddings.fembed").toBoolean()) {
      providers["fembed"] = initFastEmbedder(config)
    }
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
