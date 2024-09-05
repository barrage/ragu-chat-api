package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.tables.records.AgentsRecord

@Serializable
class Agent(
    val id: Int,
    val name: String,
    val context: String,
    val active: Boolean,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun AgentsRecord.toAgent() = Agent(
    id = this.id!!,
    name = this.name!!,
    context = this.context!!,
    active = this.active!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
)