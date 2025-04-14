package net.barrage.llmao.app.embeddings

import io.ktor.server.config.*
import net.barrage.llmao.app.embeddings.openai.AzureEmbedder
import net.barrage.llmao.app.embeddings.openai.OpenAIEmbedder
import net.barrage.llmao.app.embeddings.openai.VllmEmbedder
import net.barrage.llmao.app.llm.ModelDeploymentMap
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.int
import net.barrage.llmao.string

class EmbeddingProviderFactory(config: ApplicationConfig) : ProviderFactory<Embedder>() {
  private val log =
    io.ktor.util.logging.KtorSimpleLogger("n.b.l.a.embeddings.EmbeddingProviderFactory")

  init {
    if (config.tryGetString("ktor.features.embeddings.openai").toBoolean()) {
      with(initOpenAIEmbedder(config)) { providers[id()] = this }
    }
    if (config.tryGetString("ktor.features.embeddings.azure").toBoolean()) {
      with(initAzureEmbedder(config)) { providers[id()] = this }
    }
    if (config.tryGetString("ktor.features.embeddings.fembed").toBoolean()) {
      with(initFastEmbedder(config)) { providers[id()] = this }
    }
    if (config.tryGetString("ktor.features.embeddings.vllm").toBoolean()) {
      with(initVllmEmbedder(config)) { providers[id()] = this }
    }
  }

  private fun initOpenAIEmbedder(config: ApplicationConfig): OpenAIEmbedder {
    val endpoint = config.string("embeddings.openai.endpoint")
    val apiKey = config.string("embeddings.openai.apiKey")
    val models =
      config
        .configList("embeddings.openai.models")
        .associateBy { it.string("model") }
        .mapValues { it.value.int("vectorSize") }

    if (models.isEmpty()) {
      throw AppError.internal(
        """invalid openai configuration; Check your `embeddings.openai.models` config.
           | At least one model must be specified.
           | If you do not intend to use OpenAI, set the `ktor.features.embeddings.openai` flag to `false`.
           |"""
          .trimMargin()
      )
    }

    log.info("Initializing OpenAI embeddings with models: {}", models.keys.joinToString(", "))

    return OpenAIEmbedder(endpoint, apiKey, models)
  }

  private fun initAzureEmbedder(config: ApplicationConfig): AzureEmbedder {
    val endpoint = config.string("embeddings.azure.endpoint")
    val apiKey = config.string("embeddings.azure.apiKey")
    val apiVersion = config.string("embeddings.azure.apiVersion")
    val deploymentMap =
      ModelDeploymentMap.embeddingDeploymentMap(config.configList("embeddings.azure.models"))

    if (deploymentMap.isEmpty()) {
      throw AppError.internal(
        """invalid azure configuration; Check your `embeddings.azure.models` config.
           | At least one model must be specified.
           | If you do not intend to use Azure, set the `ktor.features.embeddings.azure` flag to `false`.
           |"""
          .trimMargin()
      )
    }

    log.info(
      "Initializing Azure embeddings with models: {}",
      deploymentMap.listModels().joinToString(", "),
    )

    return AzureEmbedder(
      endpoint = endpoint,
      apiVersion = apiVersion,
      apiKey = apiKey,
      deploymentMap = deploymentMap,
    )
  }

  private fun initFastEmbedder(config: ApplicationConfig): FastEmbedder {
    val endpoint = config.string("embeddings.fembed.endpoint")

    return FastEmbedder(endpoint)
  }

  private fun initVllmEmbedder(config: ApplicationConfig): VllmEmbedder {
    val endpoint = config.string("embeddings.vllm.endpoint")
    val apiKey = config.string("embeddings.vllm.apiKey")
    val deploymentMap =
      ModelDeploymentMap.embeddingDeploymentMap(config.configList("embeddings.vllm.models"))

    if (deploymentMap.isEmpty()) {
      throw AppError.internal(
        """invalid vllm configuration; Check your `embeddings.vllm.models` config.
           | At least one model must be specified.
           | If you do not intend to use VLLM, set the `ktor.features.embeddings.vllm` flag to `false`.
           |"""
          .trimMargin()
      )
    }

    log.info(
      "Initializing VLLM embeddings with models: {}",
      deploymentMap.listModels().joinToString(", "),
    )

    return VllmEmbedder(endpoint, apiKey, deploymentMap)
  }
}
