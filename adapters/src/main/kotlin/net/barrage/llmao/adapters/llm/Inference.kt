package net.barrage.llmao.adapters.llm

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.tryGetString
import io.ktor.server.config.tryGetStringList
import net.barrage.llmao.adapters.llm.openai.AzureAI
import net.barrage.llmao.adapters.llm.openai.OpenAI
import net.barrage.llmao.adapters.llm.openai.Vllm
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.llm.ModelDeploymentMap
import net.barrage.llmao.core.string

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
