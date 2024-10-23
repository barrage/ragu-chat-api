package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
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
    userId = this.userId,
    agentId = this.agentId,
    title = this.title,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
