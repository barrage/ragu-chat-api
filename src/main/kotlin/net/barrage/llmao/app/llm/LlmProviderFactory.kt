package net.barrage.llmao.app.llm

import io.ktor.server.config.*
import net.barrage.llmao.app.llm.openai.AzureAI
import net.barrage.llmao.app.llm.openai.Vllm
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.string

class LlmProviderFactory(config: ApplicationConfig) : ProviderFactory<LlmProvider>() {
  init {
    config.tryGetString("ktor.features.llm.openai")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["openai"] = initOpenAi(config)
      }
    }
    config.tryGetString("ktor.features.llm.azure")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["azure"] = initAzure(config)
      }
    }
    config.tryGetString("ktor.features.llm.ollama")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["ollama"] = initOllama(config)
      }
    }
    config.tryGetString("ktor.features.llm.vllm")?.toBoolean()?.let { enabled ->
      if (enabled) {
        providers["vllm"] = initVllm(config)
      }
    }
  }

  private fun initOpenAi(config: ApplicationConfig): Vllm {
    val endpoint = config.string("llm.openai.endpoint")
    val apiKey = config.string("llm.openai.apiKey")

    return Vllm(endpoint, apiKey)
  }

  private fun initAzure(config: ApplicationConfig): AzureAI {
    val endpoint = config.string("llm.azure.endpoint")
    val apiKey = config.string("llm.azure.apiKey")
    val version = config.string("llm.azure.apiVersion")

    return AzureAI(endpoint, apiKey, version)
  }

  private fun initOllama(config: ApplicationConfig): Ollama {
    val endpoint = config.string("llm.ollama.endpoint")

    return Ollama(endpoint)
  }

  private fun initVllm(config: ApplicationConfig): Vllm {
    val endpoint = config.string("llm.vllm.endpoint")
    val apiKey = config.string("llm.vllm.apiKey")

    return Vllm(endpoint, apiKey)
  }
}
