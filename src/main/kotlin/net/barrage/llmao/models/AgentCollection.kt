package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.AgentCollectionsRecord

@Serializable
data class AgentCollection(
  val id: KUUID,
  val agentId: KUUID,
  val collection: String,
  val amount: Int,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun AgentCollectionsRecord.toCollectionParams(): Pair<String, Int> {
  return Pair(collection!!, amount!!)
}
