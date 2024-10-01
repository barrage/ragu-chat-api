package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.AgentsRecord

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
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
