package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.MessagesRecord

@Serializable
class Message(
  val id: KUUID,
  val sender: KUUID,
  val senderType: String,
  val content: String,
  val chatId: KUUID,
  val responseTo: KUUID? = null,
  val evaluation: Boolean? = null,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun MessagesRecord.toMessage() =
  Message(
    id = this.id!!,
    sender = this.sender!!,
    senderType = this.senderType!!,
    content = this.content!!,
    chatId = this.chatId!!,
    responseTo = this.responseTo,
    evaluation = this.evaluation,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )