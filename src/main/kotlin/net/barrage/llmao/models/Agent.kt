package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.repositories.AgentsRepository
import net.barrage.llmao.serializers.KZonedDateTimeSerializer
import net.barrage.llmao.tables.records.AgentsRecord
import java.time.ZonedDateTime

@Serializable
class Agent (
  val id: Int,
  val name: String,
  val context: String,
  @Serializable(with = KZonedDateTimeSerializer::class)
  val createdAt: ZonedDateTime,
  @Serializable(with = KZonedDateTimeSerializer::class)
  val updatedAt: ZonedDateTime,
) {
}

fun AgentsRecord.toAgent() = Agent(
  id = this.id!!,
  name = this.name!!,
  context = this.context!!,
  createdAt = this.createdAt!!.toZonedDateTime(),
  updatedAt = this.updatedAt!!.toZonedDateTime(),
)