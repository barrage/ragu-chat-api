package net.barrage.llmao.core.models

import io.ktor.server.plugins.requestvalidation.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.Language
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
class Agent(
  val id: KUUID,
  val name: String,
  val description: String?,
  val context: String,
  val llmProvider: String,
  val model: String,
  val temperature: Double,
  val vectorProvider: String,
  val language: Language,
  val active: Boolean,
  val embeddingProvider: String,
  val embeddingModel: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentsRecord.toAgent() =
  Agent(
    id = this.id!!,
    name = this.name!!,
    description = description,
    context = this.context!!,
    llmProvider = this.llmProvider!!,
    model = this.model!!,
    temperature = this.temperature!!,
    vectorProvider = this.vectorProvider!!,
    language = Language.tryFromString(this.language!!),
    active = this.active!!,
    embeddingProvider = this.embeddingProvider!!,
    embeddingModel = this.embeddingModel!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
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
  val language: Language,
  val active: Boolean,
  @NotBlank val embeddingProvider: String,
  @NotBlank val embeddingModel: String,
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
  val language: Language? = null,
  val active: Boolean? = null,
  @NotBlank val embeddingProvider: String? = null,
  @NotBlank val embeddingModel: String? = null,
) : Validation {
  fun validateCombinations(): List<ValidationError> {
    val errors = mutableListOf<ValidationError>()
    when (Pair(llmProvider == null, model == null)) {
      Pair(false, true) ->
        errors.addSchemaErr(message = "`llmProvider` must be specified when passing `model` ")
      Pair(true, false) ->
        errors.addSchemaErr(message = "`model` must be specified when passing `llmProvider` ")
    }
    when (Pair(embeddingProvider == null, embeddingModel == null)) {
      Pair(false, true) ->
        errors.addSchemaErr(
          message = "`embeddingProvider` must be specified when passing `embeddingModel` "
        )
      Pair(true, false) ->
        errors.addSchemaErr(
          message = "`embeddingModel` must be specified when passing `embeddingProvider` "
        )
    }
    return errors
  }
}
