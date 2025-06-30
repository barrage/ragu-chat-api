package net.barrage.llmao.adapters.embeddings

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import net.barrage.llmao.adapters.embeddings.openai.AzureEmbedder
import net.barrage.llmao.adapters.embeddings.openai.OpenAIEmbedder
import net.barrage.llmao.adapters.embeddings.openai.VllmEmbedder
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.int
import net.barrage.llmao.core.llm.ModelDeploymentMap
import net.barrage.llmao.core.string

fun initializeEmbedders(config: ApplicationConfig): ProviderFactory<Embedder> =
  ProviderFactory<Embedder>().apply {
    if (config.tryGetString("ktor.features.embeddings.openai").toBoolean()) {
      val endpoint = config.string("embeddings.openai.endpoint")
      val apiKey = config.string("embeddings.openai.apiKey")
      val models =
        config
          .configList("embeddings.openai.models")
          .associateBy { it.string("model") }
          .mapValues { it.value.int("vectorSize") }
      register(OpenAIEmbedder.initialize(endpoint, apiKey, models))
    }

    if (config.tryGetString("ktor.features.embeddings.azure").toBoolean()) {
      val endpoint = config.string("embeddings.azure.endpoint")
      val apiKey = config.string("embeddings.azure.apiKey")
      val apiVersion = config.string("embeddings.azure.apiVersion")
      val deploymentMap =
        ModelDeploymentMap.embeddingDeploymentMap(config.configList("embeddings.azure.models"))
      register(AzureEmbedder.initialize(endpoint, apiKey, apiVersion, deploymentMap))
    }

    if (config.tryGetString("ktor.features.embeddings.fembed").toBoolean()) {
      register(FastEmbedder(config.string("embeddings.fembed.endpoint")))
    }

    if (config.tryGetString("ktor.features.embeddings.vllm").toBoolean()) {
      val endpoint = config.string("embeddings.vllm.endpoint")
      val apiKey = config.string("embeddings.vllm.apiKey")
      val deploymentMap =
        ModelDeploymentMap.embeddingDeploymentMap(config.configList("embeddings.vllm.models"))

      register(
        VllmEmbedder.initialize(endpoint = endpoint, apiKey = apiKey, deploymentMap = deploymentMap)
      )
    }
  }
