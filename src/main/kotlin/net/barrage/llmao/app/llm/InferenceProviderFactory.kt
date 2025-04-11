package net.barrage.llmao.app.llm

import io.ktor.server.config.*
import net.barrage.llmao.app.llm.openai.AzureAI
import net.barrage.llmao.app.llm.openai.OpenAI
import net.barrage.llmao.app.llm.openai.Vllm
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ProviderFactory
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.string

class InferenceProviderFactory(config: ApplicationConfig) : ProviderFactory<InferenceProvider>() {
  private val log = io.ktor.util.logging.KtorSimpleLogger("n.b.l.a.llm.InferenceProviderFactory")

  init {
    if (config.tryGetString("ktor.features.llm.openai").toBoolean()) {
      with(initOpenAi(config)) { providers[id()] = this }
    }

    if (config.tryGetString("ktor.features.llm.azure").toBoolean()) {
      with(initAzure(config)) { providers[id()] = this }
    }

    if (config.tryGetString("ktor.features.llm.ollama").toBoolean()) {
      with(initOllama(config)) { providers[id()] = this }
    }

    if (config.tryGetString("ktor.features.llm.vllm").toBoolean()) {
      with(initVllm(config)) { providers[id()] = this }
    }
  }

  private fun initOpenAi(config: ApplicationConfig): OpenAI {
    val endpoint = config.string("llm.openai.endpoint")
    val apiKey = config.string("llm.openai.apiKey")
    val models = config.tryGetStringList("llm.openai.models") ?: emptyList()

    if (models.isEmpty()) {
      throw AppError.internal(
        """invalid openai configuration; Check your `llm.openai.models` config.
           | At least one model must be specified.
           | If you do not intend to use OpenAI, set the `ktor.features.llm.openai` flag to `false`.
           |"""
          .trimMargin()
      )
    }

    log.info("Initializing OpenAI with models: {}", models.joinToString(", "))

    return OpenAI(endpoint, apiKey, models)
  }

  private fun initAzure(config: ApplicationConfig): AzureAI {
    val endpoint = config.string("llm.azure.endpoint")
    val apiKey = config.string("llm.azure.apiKey")
    val version = config.string("llm.azure.apiVersion")
    val deploymentMap = ModelDeploymentMap.llmDeploymentMap(config.config("llm.azure.models"))

    if (deploymentMap.isEmpty()) {
      throw AppError.internal(
        """invalid azure configuration; Check your `llm.azure.models` config.
           | At least one model must be specified.
           | If you do not intend to use Azure, set the `ktor.features.llm.azure` flag to `false`.
           |"""
          .trimMargin()
      )
    }

    log.info("Initializing Azure with models: {}", deploymentMap.listModels().joinToString(", "))

    return AzureAI(endpoint, apiKey, version, deploymentMap)
  }

  private fun initOllama(config: ApplicationConfig): Ollama {
    val endpoint = config.string("llm.ollama.endpoint")

    return Ollama(endpoint)
  }

  private fun initVllm(config: ApplicationConfig): Vllm {
    val endpoint = config.string("llm.vllm.endpoint")
    val apiKey = config.string("llm.vllm.apiKey")
    val deploymentMap = ModelDeploymentMap.llmDeploymentMap(config.config("llm.vllm.models"))

    if (deploymentMap.isEmpty()) {
      throw AppError.internal(
        """invalid vllm configuration; Check your `llm.vllm.models` config.
           | At least one model must be specified.
           | If you do not intend to use vLLM, set the `ktor.features.llm.vllm` flag to `false`.
           |"""
          .trimMargin()
      )
    }

    log.info("Initializing VLLM with models: {}", deploymentMap.listModels().joinToString(", "))

    return Vllm(endpoint, apiKey, deploymentMap)
  }
}
