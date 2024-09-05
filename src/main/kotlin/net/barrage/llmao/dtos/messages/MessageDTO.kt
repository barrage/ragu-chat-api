package net.barrage.llmao.dtos.messages

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.MessagesRecord

@Serializable
data class MessageDTO(
    val id: KUUID,
    val sender: String,
    val senderType: String,
    val content: String,
    val chatId: KUUID,
    val responseTo: KUUID?,
    val evaluation: Boolean?,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
)

fun MessagesRecord.toMessageDTO() = MessageDTO(
    id = this.id!!,
    sender = this.sender!!,
    senderType = this.senderType!!,
    content = this.content!!,
    chatId = this.chatId!!,
    responseTo = this.responseTo,
    evaluation = this.evaluation,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!
)