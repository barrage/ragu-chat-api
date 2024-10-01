package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord

/** Base model. */
@Serializable
data class Chat(
  val id: KUUID,
  val userId: KUUID,
  val agentId: KUUID,
  val title: String?,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

/** Base model with its messages. */
@Serializable data class ChatWithMessages(val chat: Chat, val messages: List<Message>)

fun ChatsRecord.toChat() =
  Chat(
    id = this.id!!,
    userId = this.userId!!,
    agentId = this.agentId!!,
    title = this.title,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
