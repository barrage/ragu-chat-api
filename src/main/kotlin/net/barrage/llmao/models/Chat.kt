package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord

@Serializable
class Chat(
    val id: KUUID,
    val userId: KUUID,
    val agentId: Int,
    val title: String,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun ChatsRecord.toChat() = Chat(
    id = this.id!!,
    userId = this.userId!!,
    agentId = this.agentId!!,
    title = this.title!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!
)