package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.LLM_CONFIGS
import org.jooq.Record

/** Base model. */
@Serializable
data class Chat(
  val id: KUUID,
  val userId: KUUID,
  val agentId: Int,
  val title: String?,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

/** Base model with its configuration. */
@Serializable data class ChatWithConfig(val chat: Chat, val config: LlmConfigModel)

/** Base model with its configuration and messages. */
@Serializable data class ChatFull(val chat: ChatWithConfig, val messages: List<Message>)

fun ChatsRecord.toChat() =
  Chat(
    id = this.id!!,
    userId = this.userId!!,
    agentId = this.agentId!!,
    title = this.title,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

fun Record.toChatWithConfig(): ChatWithConfig {
  val llmConfig = into(LLM_CONFIGS).toLlmConfig()
  val chat = into(CHATS)

  return ChatWithConfig(chat = chat.toChat(), config = llmConfig)
}
