package net.barrage.llmao.app.workflow.chat.model

import java.time.LocalDate
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.NotBlank
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.model.AgentCollection
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.model.common.TimeSeries
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentPermissionsRecord
import net.barrage.llmao.tables.records.AgentToolsRecord
import net.barrage.llmao.tables.records.AgentsRecord

@Serializable
data class Agent(
  val id: KUUID,

  /** User-friendly agent name. */
  val name: String,

  /** User friendly agent description. */
  val description: String? = null,

  /** If `true`, the agent is visible to non-admin users. */
  val active: Boolean,

  /** Agent's current active configuration. */
  val activeConfigurationId: KUUID? = null,

  /** Language the agent is configured to use. For display purposes only. */
  val language: String? = null,

  /** Agent's avatar file path including the extension. */
  val avatar: String? = null,

  /** Agents timestamps. */
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentsRecord.toAgent() =
  Agent(
    id = this.id!!,
    name = this.name,
    description = description,
    active = this.active,
    activeConfigurationId = this.activeConfigurationId,
    language = this.language,
    avatar = this.avatar,
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
  val groups: List<AgentPermission>,
  val tools: List<String>,
)

/** DTO for creating an agent. */
@Serializable
data class CreateAgent(
  @NotBlank val name: String,
  @NotBlank val description: String?,
  val active: Boolean = false,
  @NotBlank val language: String,
  val configuration: CreateAgentConfiguration,
) : Validation

/** DTO for updating an agent. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateAgent(
  @NotBlank val name: String? = null,
  @NotBlank
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val description: PropertyUpdate<String> = PropertyUpdate.Undefined,
  val active: Boolean? = null,
  @NotBlank
  @EncodeDefault(EncodeDefault.Mode.NEVER)
  val language: PropertyUpdate<String> = PropertyUpdate.Undefined,
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

@Serializable data class AgentPermission(val group: String, val createdBy: String? = null)

fun AgentPermissionsRecord.toAgentPermission() = AgentPermission(group, createdBy)

@Serializable
data class AgentGroupUpdate(val add: List<String>? = null, val remove: List<String>? = null)

/**
 * Used to create time series data for the amount of chats an agents has had in a given period.
 *
 * Here * `Long` represents the amount of chats and `String` represents the agent name.
 */
typealias AgentChatTimeSeries = @Serializable TimeSeries<Long, String>

@Serializable data class AgentTool(val id: KUUID, val agentId: KUUID, val toolName: String)

fun AgentToolsRecord.toAgentTool() = AgentTool(id = id!!, agentId = agentId, toolName = toolName)
