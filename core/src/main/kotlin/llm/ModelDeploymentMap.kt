package net.barrage.llmao.core.llm

import io.ktor.server.config.ApplicationConfig
import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.int
import net.barrage.llmao.core.string

private val LOG = KtorSimpleLogger("n.b.l.a.llm.ModelDeploymentMap")

@JvmInline value class ModelCode(val code: String)

@JvmInline value class DeploymentId(val id: String)

data class EmbeddingModelDeployment(val deploymentId: String, val vectorSize: Int)

@JvmInline
value class ModelDeploymentMap<T>(internal val m: Map<ModelCode, T>) {
  companion object {
    fun llmDeploymentMap(config: ApplicationConfig): ModelDeploymentMap<DeploymentId> {
      val modelMap = mutableMapOf<ModelCode, DeploymentId>()

      for ((deploymentId, modelCode) in config.toMap()) {
        val model = modelCode as String

        if (model.isBlank()) {
          LOG.warn("Model for deployment '$deploymentId' is blank, skipping")
          continue
        }

        modelMap[ModelCode(model)] = DeploymentId(deploymentId)
      }

      return ModelDeploymentMap(modelMap)
    }

    fun embeddingDeploymentMap(
      modelConfig: List<ApplicationConfig>
    ): ModelDeploymentMap<EmbeddingModelDeployment> {
      val modelMap = mutableMapOf<ModelCode, EmbeddingModelDeployment>()

      for (config in modelConfig) {
        val deploymentId = config.string("deploymentId")
        val modelCode = config.string("model")
        val vectorSize = config.int("vectorSize")
        modelMap[ModelCode(modelCode)] = EmbeddingModelDeployment(deploymentId, vectorSize)
      }

      return ModelDeploymentMap(modelMap)
    }
  }

  operator fun get(model: String): T? = m[ModelCode(model)]

  fun listModels(): List<String> = m.keys.map(ModelCode::code)

  fun isEmpty() = m.isEmpty()

  fun containsKey(model: String) = m.containsKey(ModelCode(model))
}
