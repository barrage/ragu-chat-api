package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentConfigurationsRecord
import net.barrage.llmao.utils.NotBlank
import net.barrage.llmao.utils.Range
import net.barrage.llmao.utils.SchemaValidation
import net.barrage.llmao.utils.Validation
import net.barrage.llmao.utils.ValidationError
import net.barrage.llmao.utils.addSchemaErr

@Serializable
class AgentConfiguration(
  val id: KUUID,
  val agentId: KUUID,

  /** Tag of the configuration. */
  val version: Int,

  /** Sent as a system message upon chat completion. Defines how an agent behaves. */
  val context: String,

  /** LLM provider, e.g. openai, azure, ollama, etc. */
  val llmProvider: String,

  /** LLM, e.g. gpt-4, mistral, etc. */
  val model: String,

  /** LLM LSD consumption amount. */
  val temperature: Double,

  /** Instructions for the agent to use. */
  val agentInstructions: AgentInstructions,

  /** Agent configuration timestamps. */
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentConfigurationsRecord.toAgentConfiguration() =
  AgentConfiguration(
    id = this.id!!,
    agentId = this.agentId,
    version = this.version,
    context = this.context,
    llmProvider = this.llmProvider,
    model = this.model,
    temperature = this.temperature!!,
    agentInstructions =
      AgentInstructions(this.titleInstruction, this.languageInstruction, this.summaryInstruction),
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

@Serializable
class CreateAgentConfiguration(
  @NotBlank val context: String,
  @NotBlank val llmProvider: String,
  @NotBlank val model: String,
  @Range(min = 0.0, max = 1.0) val temperature: Double,
  val instructions: AgentInstructions? = null,
) : Validation

@Serializable
@SchemaValidation("validateCombinations")
class UpdateAgentConfiguration(
  @NotBlank val context: String? = null,
  @NotBlank val llmProvider: String? = null,
  @NotBlank val model: String? = null,
  @Range(min = 0.0, max = 1.0) val temperature: Double? = null,
  val instructions: AgentInstructions? = null,
) : Validation {
  fun validateCombinations(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    when (Pair(llmProvider == null, model == null)) {
      Pair(false, true) ->
        errors.addSchemaErr(message = "`model` must be specified when passing `llmProvider`")
      Pair(true, false) ->
        errors.addSchemaErr(message = "`llmProvider` must be specified when passing `model`")
    }
    return errors
  }
}

@Serializable
class AgentConfigurationEvaluatedMessageCounts(
  val total: Int,
  val positive: Int,
  val negative: Int,
)

@Serializable
class AgentConfigurationWithEvaluationCounts(
  val configuration: AgentConfiguration,
  val evaluationCounts: AgentConfigurationEvaluatedMessageCounts,
)
