package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.AgentCollectionsRecord
import net.barrage.llmao.utils.NotBlank
import net.barrage.llmao.utils.Validation

@Serializable
data class AgentCollection(
  val id: KUUID,
  val agentId: KUUID,
  val collection: String,
  val amount: Int,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentCollectionsRecord.toCollection(): AgentCollection {
  return AgentCollection(
    id = id!!,
    agentId = agentId!!,
    collection = collection!!,
    amount = amount!!,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!,
  )
}

/**
 * Update DTO for an agent's knowledge base.
 *
 * `add` keys are collection names, and values are the amount of vectors to retrieve per collection
 * to add to the agent.
 *
 * `remove` is a list of collections to remove from the knowledge base.
 *
 * The `provider` is necessary and is used to determine which vector database to update in.
 */
@Serializable
data class UpdateCollections(
  @NotBlank val provider: String,
  val add: Map<String, Int>?,
  val remove: List<String>?,
) : Validation
