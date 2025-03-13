package net.barrage.llmao.core.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.NotBlank
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.vector.VectorCollectionInfo
import net.barrage.llmao.tables.records.AgentCollectionsRecord

@Serializable
data class AgentCollection(
  val id: KUUID,
  val agentId: KUUID,
  val instruction: String,
  val collection: String,
  val amount: Int,
  val embeddingProvider: String,
  val embeddingModel: String,
  val vectorProvider: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentCollectionsRecord.toAgentCollection(): AgentCollection {
  return AgentCollection(
    id = id!!,
    agentId = agentId,
    instruction = instruction,
    collection = collection,
    amount = amount,
    embeddingProvider = embeddingProvider,
    embeddingModel = embeddingModel,
    vectorProvider = vectorProvider,
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
  val add: List<UpdateCollectionAddition>? = null,
  val remove: List<CollectionRemove>? = null,
) : Validation

@Serializable
data class UpdateCollectionAddition(
  @NotBlank val provider: String,
  @NotBlank val name: String,
  val amount: Int,
  val instruction: String,
) : Validation

@Serializable
data class UpdateCollectionsResult(
  val added: List<VectorCollectionInfo>,
  val removed: List<CollectionRemove>,
  val failed: List<UpdateCollectionsFailure>,
)

@Serializable data class UpdateCollectionsFailure(val name: String, val reason: String)

data class CollectionInsert(
  val amount: Int,
  val instruction: String,
  val info: VectorCollectionInfo,
)

@Serializable data class CollectionRemove(val name: String, val provider: String)
