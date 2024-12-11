package net.barrage.llmao.app.adapters.whatsapp.dto

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.UsersRecord
import net.barrage.llmao.tables.records.WhatsAppAgentCollectionsRecord
import net.barrage.llmao.tables.records.WhatsAppAgentsRecord
import net.barrage.llmao.tables.records.WhatsAppChatsRecord
import net.barrage.llmao.tables.records.WhatsAppMessagesRecord

@Serializable
data class WhatsAppAgentDTO(
  val id: KUUID,
  val name: String,
  val description: String?,
  val context: String,
  val llmProvider: String,
  val model: String,
  val temperature: Double,
  val language: String,
  val active: Boolean,
  val agentInstructions: AgentInstructions,
) {
  fun language(): String {
    return agentInstructions.language()
  }
}

@Serializable
data class WhatsAppAgentCollectionDTO(
  val id: KUUID,
  val agentId: KUUID,
  val instruction: String,
  val collection: String,
  val amount: Int,
  val embeddingModel: String,
  val vectorProvider: String,
  val embeddingProvider: String,
) {
  fun toCollection(): AgentCollection {
    return AgentCollection(
      id = id,
      agentId = agentId,
      instruction = instruction,
      collection = collection,
      amount = amount,
      embeddingProvider = embeddingProvider,
      embeddingModel = embeddingModel,
      vectorProvider = vectorProvider,
      createdAt = KOffsetDateTime.now(),
      updatedAt = KOffsetDateTime.now(),
    )
  }
}

@Serializable data class WhatsAppChatDTO(val id: KUUID, val userId: KUUID)

@Serializable
data class WhatsAppChatWithUserNameDTO(val chat: WhatsAppChatDTO, val fullName: String)

@Serializable
data class UserDTO(
  val id: KUUID,
  val email: String,
  val fullName: String,
  val firstName: String,
  val lastName: String,
  val active: Boolean,
  val role: Role,
)

@Serializable
data class WhatsAppMessageDTO(
  val id: KUUID,
  val sender: KUUID,
  val senderType: String,
  val content: String,
  val chatId: KUUID,
  val responseTo: KUUID? = null,
)

fun WhatsAppAgentsRecord.toWhatsAppAgentDTO() =
  WhatsAppAgentDTO(
    id = this.id!!,
    name = this.name,
    description = this.description,
    context = this.context,
    llmProvider = this.llmProvider,
    model = this.model,
    temperature = this.temperature!!,
    language = this.language!!,
    active = this.active!!,
    agentInstructions =
      AgentInstructions(
        languageInstruction = this.languageInstruction,
        summaryInstruction = this.summaryInstruction,
      ),
  )

fun WhatsAppAgentCollectionsRecord.toWhatsAppAgentCollectionDTO() =
  WhatsAppAgentCollectionDTO(
    id = this.id!!,
    agentId = this.agentId!!,
    instruction = this.instruction,
    collection = this.collection,
    amount = this.amount,
    embeddingModel = this.embeddingModel,
    vectorProvider = this.vectorProvider,
    embeddingProvider = this.embeddingProvider,
  )

fun WhatsAppChatsRecord.toWhatsAppChatDTO() = WhatsAppChatDTO(id = this.id!!, userId = this.userId)

fun UsersRecord.toUserDTO() =
  UserDTO(
    id = this.id!!,
    email = this.email,
    fullName = this.fullName,
    firstName = this.firstName!!,
    lastName = this.lastName!!,
    role = Role.valueOf(this.role),
    active = this.active!!,
  )

fun WhatsAppMessagesRecord.toWhatsAppMessageDTO() =
  WhatsAppMessageDTO(
    id = this.id!!,
    sender = this.sender,
    senderType = this.senderType,
    content = this.content,
    chatId = this.chatId,
    responseTo = this.responseTo,
  )
