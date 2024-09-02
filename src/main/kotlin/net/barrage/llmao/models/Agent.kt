package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTimeSerializer
import net.barrage.llmao.tables.records.AgentsRecord
import java.time.OffsetDateTime

@Serializable
class Agent(
    val id: Int,
    val name: String,
    val context: String,
    val active: Boolean,
    @Serializable(with = KOffsetDateTimeSerializer::class)
    val createdAt: OffsetDateTime,
    @Serializable(with = KOffsetDateTimeSerializer::class)
    val updatedAt: OffsetDateTime,
)

fun AgentsRecord.toAgent() = Agent(
    id = this.id!!,
    name = this.name!!,
    context = this.context!!,
    active = this.active!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
)