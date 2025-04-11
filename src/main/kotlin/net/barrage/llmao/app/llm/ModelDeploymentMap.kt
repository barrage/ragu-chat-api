package net.barrage.llmao.app.llm

import io.ktor.server.config.ApplicationConfig
import net.barrage.llmao.int
import net.barrage.llmao.string

private val LOG = io.ktor.util.logging.KtorSimpleLogger("n.b.l.a.llm.ModelDeploymentMap")

@JvmInline value class ModelCode(internal val code: String)

@JvmInline value class DeploymentId(internal val id: String)

data class EmbeddingModelDeployment(
  internal val deploymentId: String,
  internal val vectorSize: Int,
)

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

  operator fun get(model: String): T? {
    return m[ModelCode(model)]
  }

  fun listModels(): List<String> {
    return m.keys.map(ModelCode::code)
  }

  fun isEmpty() = m.isEmpty()
}
