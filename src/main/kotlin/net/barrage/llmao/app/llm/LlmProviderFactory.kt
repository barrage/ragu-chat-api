package net.barrage.llmao.app.llm

import io.ktor.server.application.*
import net.barrage.llmao.core.LlmFactory
import net.barrage.llmao.core.llm.ConversationLlm
import net.barrage.llmao.env
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class LlmProviderFactory(env: ApplicationEnvironment) : LlmFactory() {
  private val openai: OpenAI
  private val azure: AzureAI

  init {
    openai = initOpenAi(env)
    azure = initAzure(env)
  }

  override fun getProvider(providerId: String): ConversationLlm {
    return when (providerId) {
      openai.id() -> openai
      azure.id() -> azure
      else ->
        throw AppError.api(ErrorReason.InvalidProvider, "Unsupported LLM provider '$providerId'")
    }
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
}
