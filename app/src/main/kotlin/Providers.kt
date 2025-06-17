package net.barrage.llmao.app

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList
import net.barrage.llmao.adapters.blob.MinioImageStorage
import net.barrage.llmao.adapters.embeddings.FastEmbedder
import net.barrage.llmao.adapters.embeddings.openai.AzureEmbedder
import net.barrage.llmao.adapters.embeddings.openai.OpenAIEmbedder
import net.barrage.llmao.adapters.embeddings.openai.VllmEmbedder
import net.barrage.llmao.adapters.llm.Ollama
import net.barrage.llmao.adapters.llm.openai.AzureAI
import net.barrage.llmao.adapters.llm.openai.OpenAI
import net.barrage.llmao.adapters.llm.openai.Vllm
import net.barrage.llmao.adapters.vector.Weaviate
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.int
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.llm.ModelDeploymentMap
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.string
import net.barrage.llmao.core.vector.VectorDatabase

fun initializeInference(config: ApplicationConfig): ProviderFactory<InferenceProvider> =
  ProviderFactory<InferenceProvider>().apply {
    if (config.tryGetString("ktor.features.llm.openai").toBoolean()) {
      val endpoint = config.string("llm.openai.endpoint")
      val apiKey = config.string("llm.openai.apiKey")
      val models = config.tryGetStringList("llm.openai.models") ?: emptyList()
      register(OpenAI.initialize(endpoint, apiKey, models))
    }

    if (config.tryGetString("ktor.features.llm.azure").toBoolean()) {
      val endpoint = config.string("llm.azure.endpoint")
      val apiKey = config.string("llm.azure.apiKey")
      val apiVersion = config.string("llm.azure.apiVersion")
      val deploymentMap =
        ModelDeploymentMap.Companion.llmDeploymentMap(config.config("llm.azure.models"))
      register(AzureAI.initialize(endpoint, apiKey, apiVersion, deploymentMap))
    }

    if (config.tryGetString("ktor.features.llm.ollama").toBoolean()) {
      val endpoint = config.string("llm.ollama.endpoint")
      register(Ollama.initialize(endpoint))
    }

    if (config.tryGetString("ktor.features.llm.vllm").toBoolean()) {
      val endpoint = config.string("llm.vllm.endpoint")
      val apiKey = config.string("llm.vllm.apiKey")
      val deploymentMap = ModelDeploymentMap.llmDeploymentMap(config.config("llm.vllm.models"))
      register(Vllm.initialize(endpoint, apiKey, deploymentMap))
    }
  }

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

fun initializeVectorDatabases(config: ApplicationConfig): ProviderFactory<VectorDatabase> =
  ProviderFactory<VectorDatabase>().apply {
    // Weaviate is always enabled
    register(Weaviate.initialize(config.string("weaviate.scheme"), config.string("weaviate.host")))
  }

fun initializeImageStorage(config: ApplicationConfig): BlobStorage<Image> {
  return MinioImageStorage(
    config.string("minio.bucket"),
    config.string("minio.endpoint"),
    config.string("minio.accessKey"),
    config.string("minio.secretKey"),
  )
}
