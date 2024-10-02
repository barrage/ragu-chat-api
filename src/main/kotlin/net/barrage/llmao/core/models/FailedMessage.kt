package net.barrage.llmao.core.models

import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.FailedMessagesRecord

class FailedMessage(
  val id: KUUID,
  val failReason: String,
  val userId: KUUID,
  val content: String,
  val chatId: KUUID,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun FailedMessagesRecord.toFailedMessage(): FailedMessage {
  return FailedMessage(
    id = this.id!!,
    failReason = this.failReason!!,
    userId = this.userId!!,
    content = this.content!!,
    chatId = this.chatId!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
}
