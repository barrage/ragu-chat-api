package net.barrage.llmao.app.llm

import io.ktor.server.application.*
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.llm.ConversationLlm
import net.barrage.llmao.env
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class LlmProviderFactory(env: ApplicationEnvironment) : ProviderFactory<ConversationLlm>() {
  private val openai: OpenAI
  private val azure: AzureAI
  private val ollama: Ollama

  init {
    openai = initOpenAi(env)
    azure = initAzure(env)
    ollama = initOllama(env)
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

  private fun initOpenAi(env: ApplicationEnvironment): OpenAI {
    val apiKey = env(env, "llm.openAi.apiKey")

    return OpenAI(apiKey)
  }

  private fun initAzure(env: ApplicationEnvironment): AzureAI {
    val apiKey = env(env, "llm.azure.apiKey")
    val endpoint = env(env, "llm.azure.endpoint")
    val version = env(env, "llm.azure.apiVersion")

    return AzureAI(apiKey, endpoint, version)
  }

  private fun initOllama(env: ApplicationEnvironment): Ollama {
    val endpoint = env(env, "llm.ollama.endpoint")

    return Ollama(endpoint)
  }
}
