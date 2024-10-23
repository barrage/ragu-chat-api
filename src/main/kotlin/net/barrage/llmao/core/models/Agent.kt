package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.utils.NotBlank
import net.barrage.llmao.utils.Range
import net.barrage.llmao.utils.SchemaValidation
import net.barrage.llmao.utils.Validation
import net.barrage.llmao.utils.ValidationError
import net.barrage.llmao.utils.addSchemaErr

@Serializable
data class Agent(
  val id: KUUID,

  /** User friendly agent name. */
  val name: String,

  /** User friendly agent description. */
  val description: String?,

  /** Sent as a system message upon chat completion. Defines how an agent behaves. */
  val context: String,

  /** LLM provider, e.g. openai, azure, ollama, etc. */
  val llmProvider: String,

  /** LLM, e.g. gpt-4, mistral, etc. */
  val model: String,

  /** LLM LSD consumption amount. */
  val temperature: Double,

  /** Vector database provider, e.g. weaviate */
  val vectorProvider: String,

  /** Hints to the user what the expected language of this agent is. */
  val language: String,

  /** If `true`, the agent is visible to non-admin users. */
  val active: Boolean,

  /** Which embedding provider to use, e.g. azure, fembed. */
  val embeddingProvider: String,

  /** Which embedding model to use, must be supported by provider. */
  val embeddingModel: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentsRecord.toAgent() =
  Agent(
    id = this.id!!,
    name = this.name,
    description = description,
    context = this.context,
    llmProvider = this.llmProvider,
    model = this.model,
    temperature = this.temperature!!,
    vectorProvider = this.vectorProvider,
    language = this.language!!,
    active = this.active!!,
    embeddingProvider = this.embeddingProvider,
    embeddingModel = this.embeddingModel,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

@Serializable
data class AgentFull(
  val agent: Agent,
  val instructions: AgentInstructions,
  val collections: List<AgentCollection>,
)

@Serializable
data class CreateAgent(
  @NotBlank val name: String,
  @NotBlank val description: String?,
  @NotBlank val context: String,
  @NotBlank val llmProvider: String,
  @NotBlank val model: String,
  @Range(min = 0.0, max = 1.0) val temperature: Double,
  @NotBlank val vectorProvider: String,
  @NotBlank val language: String,
  val active: Boolean,
  @NotBlank val embeddingProvider: String,
  @NotBlank val embeddingModel: String,
  val instructions: AgentInstructions? = null,
) : Validation

@Serializable
@SchemaValidation("validateCombinations")
data class UpdateAgent(
  @NotBlank val name: String? = null,
  @NotBlank val description: String? = null,
  @NotBlank val context: String? = null,
  @NotBlank val llmProvider: String? = null,
  @NotBlank val model: String? = null,
  @Range(min = 0.0, max = 1.0) val temperature: Double? = null,
  @NotBlank val vectorProvider: String? = null,
  @NotBlank val language: String? = null,
  val active: Boolean? = null,
  @NotBlank val embeddingProvider: String? = null,
  @NotBlank val embeddingModel: String? = null,
  val instructions: AgentInstructions? = null,
) : Validation {
  fun validateCombinations(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    when (Pair(llmProvider == null, model == null)) {
      Pair(false, true) ->
        errors.addSchemaErr(message = "`llmProvider` must be specified when passing `model`")
      Pair(true, false) ->
        errors.addSchemaErr(message = "`model` must be specified when passing `llmProvider`")
    }
    when (Pair(embeddingProvider == null, embeddingModel == null)) {
      Pair(false, true) ->
        errors.addSchemaErr(
          message = "`embeddingModel` must be specified when passing `embeddingProvider`"
        )
      Pair(true, false) ->
        errors.addSchemaErr(
          message = "`embeddingProvider` must be specified when passing `embeddingModel`"
        )
    }
    return errors
  }
}
