package net.barrage.llmao.app.llm

import io.ktor.server.config.*
import net.barrage.llmao.configString
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.llm.ConversationLlm
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class LlmProviderFactory(config: ApplicationConfig) : ProviderFactory<ConversationLlm>() {
  private val openai: OpenAI
  private val azure: AzureAI
  private val ollama: Ollama

  init {
    openai = initOpenAi(config)
    azure = initAzure(config)
    ollama = initOllama(config)
  }

  override fun getProvider(providerId: String): ConversationLlm {
    return when (providerId) {
      openai.id() -> openai
      azure.id() -> azure
      ollama.id() -> ollama
      else ->
        throw AppError.api(ErrorReason.InvalidProvider, "Unsupported LLM provider '$providerId'")
    }
  }

  override fun listProviders(): List<String> {
    return listOf(openai.id(), azure.id(), ollama.id())
  }

  private fun initOpenAi(config: ApplicationConfig): OpenAI {
    val endpoint = configString(config, "llm.openAi.endpoint")
    val apiKey = configString(config, "llm.openAi.apiKey")

    return OpenAI(endpoint, apiKey)
  }

  private fun initAzure(config: ApplicationConfig): AzureAI {
    val endpoint = configString(config, "llm.azure.endpoint")
    val apiKey = configString(config, "llm.azure.apiKey")
    val version = configString(config, "llm.azure.apiVersion")

    return AzureAI(endpoint, apiKey, version)
  }

  private fun initOllama(config: ApplicationConfig): Ollama {
    val endpoint = configString(config, "llm.ollama.endpoint")

    return Ollama(endpoint)
  }
}
