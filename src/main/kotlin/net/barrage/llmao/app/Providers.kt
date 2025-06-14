package net.barrage.llmao.app

import embeddings.FastEmbedder
import embeddings.openai.AzureEmbedder
import embeddings.openai.OpenAIEmbedder
import embeddings.openai.VllmEmbedder
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import llm.Ollama
import llm.openai.AzureAI
import llm.openai.OpenAI
import llm.openai.Vllm
import net.barrage.llmao.app.vector.Weaviate
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.embedding.Embedder
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.vector.VectorDatabase

fun initializeInference(config: ApplicationConfig): ProviderFactory<InferenceProvider> =
  ProviderFactory<InferenceProvider>().apply {
    if (config.tryGetString("ktor.features.llm.openai").toBoolean()) {
      with(OpenAI.initialize(config)) { register(this) }
    }

    if (config.tryGetString("ktor.features.llm.azure").toBoolean()) {
      with(AzureAI.initialize(config)) { register(this) }
    }

    if (config.tryGetString("ktor.features.llm.ollama").toBoolean()) {
      with(Ollama.initialize(config)) { register(this) }
    }

    if (config.tryGetString("ktor.features.llm.vllm").toBoolean()) {
      with(Vllm.initialize(config)) { register(this) }
    }
  }

fun initializeEmbedders(config: ApplicationConfig): ProviderFactory<Embedder> =
  ProviderFactory<Embedder>().apply {
    if (config.tryGetString("ktor.features.embeddings.openai").toBoolean()) {
      with(OpenAIEmbedder.initialize(config)) { register(this) }
    }
    if (config.tryGetString("ktor.features.embeddings.azure").toBoolean()) {
      with(AzureEmbedder.initialize(config)) { register(this) }
    }
    if (config.tryGetString("ktor.features.embeddings.fembed").toBoolean()) {
      with(FastEmbedder.initialize(config)) { register(this) }
    }
    if (config.tryGetString("ktor.features.embeddings.vllm").toBoolean()) {
      with(VllmEmbedder.initialize(config)) { register(this) }
    }
  }

fun initializeVectorDatabases(config: ApplicationConfig): ProviderFactory<VectorDatabase> =
  ProviderFactory<VectorDatabase>().apply {
    // Weaviate is always enabled
    with(Weaviate.initialize(config)) { register(this) }
  }
