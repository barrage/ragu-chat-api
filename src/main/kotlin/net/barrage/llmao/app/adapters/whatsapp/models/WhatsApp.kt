package net.barrage.llmao.app.adapters.whatsapp.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.models.Image
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.WhatsAppAgentCollectionsRecord
import net.barrage.llmao.tables.records.WhatsAppAgentsRecord
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
data class WhatsAppAgent(
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
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
  var avatar: Image? = null,
)

@Serializable
data class WhatsAppAgentFull(val agent: WhatsAppAgent, val collections: List<AgentCollection>) {
  fun getConfiguration(): AgentConfiguration {
    return AgentConfiguration(
      id = agent.id,
      agentId = agent.id,
      version = 1,
      context = agent.context,
      llmProvider = agent.llmProvider,
      model = agent.model,
      temperature = agent.temperature,
      agentInstructions = agent.agentInstructions,
      createdAt = KOffsetDateTime.now(),
      updatedAt = KOffsetDateTime.now(),
    )
  }
}

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
  var avatar: Image? = null,
)

@Serializable data class WhatsAppAgentCurrent(val id: KUUID?, val active: Boolean)

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

fun WhatsAppAgentsRecord.toWhatsAppAgent() =
  WhatsAppAgent(
    id = this.id!!,
    name = this.name,
    description = this.description,
    context = this.context,
    llmProvider = this.llmProvider,
    model = this.model,
    temperature = this.temperature!!,
    language = this.language!!,
    active = this.active!!,
    agentInstructions = AgentInstructions(summaryInstruction = this.summaryInstruction),
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun WhatsAppAgentCollectionsRecord.toAgentCollection() =
  AgentCollection(
    id = this.id!!,
    agentId = this.agentId!!,
    instruction = this.instruction,
    collection = this.collection,
    amount = this.amount,
    embeddingModel = this.embeddingModel,
    vectorProvider = this.vectorProvider,
    embeddingProvider = this.embeddingProvider,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun WhatsAppAgentsRecord.toWhatsAppAgentCurrent() =
  WhatsAppAgentCurrent(id = this.id, active = this.active!!)
