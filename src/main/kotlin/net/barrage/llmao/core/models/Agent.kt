package net.barrage.llmao.core.models

import java.time.LocalDate
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.common.TimeSeries
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.utils.NotBlank
import net.barrage.llmao.utils.Validation

@Serializable
data class Agent(
  val id: KUUID,

  /** User friendly agent name. */
  val name: String,

  /** User friendly agent description. */
  val description: String?,

  /** Which vector provider to use, e.g. Weaviate/Qdrant. */
  val vectorProvider: String,

  /** Which embedding provider to use, e.g. azure, fembed. */
  val embeddingProvider: String,

  /** Which embedding model to use, must be supported by provider. */
  val embeddingModel: String,

  /** If `true`, the agent is visible to non-admin users. */
  val active: Boolean,

  /** Agents current active configuration. */
  val activeConfigurationId: KUUID? = null,

  /** Language the agent is configured to use. For display purposes only. */
  val language: String,

  /** Agents timestamps. */
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentsRecord.toAgent() =
  Agent(
    id = this.id!!,
    name = this.name,
    description = description,
    vectorProvider = this.vectorProvider,
    embeddingProvider = this.embeddingProvider,
    embeddingModel = this.embeddingModel,
    active = this.active!!,
    activeConfigurationId = this.activeConfigurationId,
    language = this.language!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

/** DTO holding an agent with its configuration. */
@Serializable
data class AgentWithConfiguration(val agent: Agent, val configuration: AgentConfiguration)

/** DTO holding an agent with its configuration and collections. */
@Serializable
data class AgentFull(
  val agent: Agent,
  val configuration: AgentConfiguration,
  val collections: List<AgentCollection>,
)

/** DTO for creating an agent. */
@Serializable
data class CreateAgent(
  @NotBlank val name: String,
  @NotBlank val description: String?,
  val active: Boolean,
  val vectorProvider: String,
  val embeddingProvider: String,
  val embeddingModel: String,
  @NotBlank val language: String,
  val configuration: CreateAgentConfiguration,
) : Validation

/** DTO for updating an agent. */
@Serializable
data class UpdateAgent(
  @NotBlank val name: String? = null,
  @NotBlank val description: String? = null,
  val active: Boolean? = null,
  @NotBlank val language: String? = null,
  val configuration: UpdateAgentConfiguration? = null,
) : Validation

/** Holds data representing the amount of chats opened for an agent on a given date. */
data class AgentChatsOnDate(
  /** Agent ID. */
  val agentId: KUUID,
  /** Agent name. */
  val agentName: String,
  /** Date. */
  val date: LocalDate?,
  /** Amount of chats opened. */
  val amount: Long,
)

/**
 * Used to create time series data for the amount of chats an agents has had in a given period.
 *
 * Here * `Long` represents the amount of chats and `String` represents the agent name.
 */
typealias AgentChatTimeSeries = @Serializable TimeSeries<Long, String>
