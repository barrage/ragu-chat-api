package net.barrage.llmao.app.adapters.whatsapp.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.WhatsAppChatsRecord
import net.barrage.llmao.tables.records.WhatsAppMessagesRecord
import net.barrage.llmao.tables.records.WhatsAppNumbersRecord
import net.barrage.llmao.utils.Number
import net.barrage.llmao.utils.Validation

@Serializable
data class WhatsAppNumber(
  val id: KUUID,
  val userId: KUUID,
  val phoneNumber: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable data class PhoneNumber(@Number val phoneNumber: String) : Validation

@Serializable
data class WhatsAppChat(
  val id: KUUID,
  val userId: KUUID,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

@Serializable
data class WhatsAppMessage(
  val id: KUUID,
  val sender: KUUID,
  val senderType: String,
  val content: String,
  val chatId: KUUID,
  val responseTo: KUUID? = null,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
) {
  fun toChatMessage(): ChatMessage {
    return ChatMessage(senderType, content)
  }
}

@Serializable
data class WhatsAppChatWithUserAndMessages(
  val chat: WhatsAppChat,
  val user: User,
  val messages: List<WhatsAppMessage>,
)

@Serializable
data class WhatsAppChatWithUserName(
  val chat: WhatsAppChat,
  val fullName: String,
  val avatar: String? = null,
)

fun WhatsAppNumbersRecord.toWhatsAppNumber() =
  WhatsAppNumber(
    id = this.id!!,
    userId = this.userId,
    phoneNumber = this.phoneNumber,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun WhatsAppChatsRecord.toWhatsAppChat() =
  WhatsAppChat(
    id = this.id!!,
    userId = this.userId,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun WhatsAppMessagesRecord.toWhatsAppMessage() =
  WhatsAppMessage(
    id = this.id!!,
    sender = this.sender,
    senderType = this.senderType,
    content = this.content,
    chatId = this.chatId,
    responseTo = this.responseTo,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
