package net.barrage.llmao.app.llm

import io.ktor.server.config.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.string

class LlmProviderFactory(config: ApplicationConfig) : ProviderFactory<LlmProvider>() {
  private val providers = mutableMapOf<String, LlmProvider>()

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
  }

  override fun getProvider(providerId: String): LlmProvider {
    return providers[providerId]
      ?: throw AppError.api(ErrorReason.InvalidProvider, "Unsupported LLM provider '$providerId'")
  }

  override fun listProviders(): List<String> {
    return providers.keys.toList()
  }

  private fun initOpenAi(config: ApplicationConfig): OpenAI {
    val endpoint = config.string("llm.openai.endpoint")
    val apiKey = config.string("llm.openai.apiKey")

    return OpenAI(endpoint, apiKey)
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
}
