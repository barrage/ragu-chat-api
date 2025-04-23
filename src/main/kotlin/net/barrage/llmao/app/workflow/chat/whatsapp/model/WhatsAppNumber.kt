package net.barrage.llmao.app.workflow.chat.whatsapp.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.Number
import net.barrage.llmao.core.Validation
import net.barrage.llmao.tables.records.WhatsAppNumbersRecord
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

@Serializable
data class WhatsAppNumber(
  val id: KUUID,
  val userId: String,
  val username: String?,
  val phoneNumber: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable data class UpdateNumber(@Number val phoneNumber: String) : Validation

fun WhatsAppNumbersRecord.toWhatsAppNumber() =
  WhatsAppNumber(
    id = this.id!!,
    userId = this.userId,
    username = this.username,
    phoneNumber = this.phoneNumber,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
