package net.barrage.llmao.core.model

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.NotBlank
import net.barrage.llmao.core.Range
import net.barrage.llmao.core.SchemaValidation
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.ValidationError
import net.barrage.llmao.core.addSchemaErr
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentConfigurationsRecord

@Serializable
data class AgentConfiguration(
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

  /** Maximum number of tokens to generate. */
  val maxCompletionTokens: Int?,

  /**
   * Number between -2.0 and 2.0. Positive values penalize new tokens based on whether they appear
   * in the text so far, increasing the model's likelihood to talk about new topics.
   */
  val presencePenalty: Double?,

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
      AgentInstructions(titleInstruction = this.titleInstruction, errorMessage = this.errorMessage),
    maxCompletionTokens = this.maxCompletionTokens,
    presencePenalty = this.presencePenalty,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

@Serializable
data class CreateAgentConfiguration(
  @NotBlank val context: String,
  @NotBlank val llmProvider: String,
  @NotBlank val model: String,
  @Range(min = 0.0, max = 1.0) val temperature: Double? = 0.1,
  @Range(min = 1.0) val maxCompletionTokens: Int? = null,
  @Range(min = -2.0, max = 2.0) val presencePenalty: Double? = 0.0,
  val instructions: AgentInstructions? = null,
) : Validation

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@SchemaValidation("validateCombinations")
data class UpdateAgentConfiguration(
  @NotBlank val context: String? = null,
  @NotBlank val llmProvider: String? = null,
  @NotBlank val model: String? = null,
  @Range(min = 0.0, max = 1.0) val temperature: Double? = null,

  /** Max completion tokens the agent is allowed to generate during completion. */
  @Range(min = 1.0)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val maxCompletionTokens: PropertyUpdate<Int> = PropertyUpdate.Undefined,

  /** Repetition penalty. */
  @Range(min = -2.0, max = 2.0)
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val presencePenalty: PropertyUpdate<Double> = PropertyUpdate.Undefined,
  val instructions: UpdateAgentInstructions? = null,
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
data class AgentConfigurationEvaluatedMessageCounts(
  val total: Int,
  val positive: Int,
  val negative: Int,
)

@Serializable
data class AgentConfigurationWithEvaluationCounts(
  val configuration: AgentConfiguration,
  val evaluationCounts: AgentConfigurationEvaluatedMessageCounts,
)
