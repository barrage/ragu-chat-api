package net.barrage.llmao.app.workflow.chat.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.CharRange
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

/** Base model with its messages. */
@Serializable
data class ChatWithMessages(val chat: Chat, val messages: CountedList<MessageGroupAggregate>)

@Serializable data class ChatWithAgent(val chat: Chat, val agent: Agent)

/** TABLE: chats */
@Serializable
data class Chat(
  val id: KUUID,
  val agentId: KUUID,

  /**
   * The ID of the user who created the chat. Used to link to the user's account on the auth server.
   */
  val userId: String,

  /** Username at the time of chat creation. Used for display purposes. */
  val username: String?,

  /** Optional title. Certain chat implementations can have auto-generated titles. */
  val title: String?,

  /**
   * The base model holds the type as a string. Implementations should take care to use enumerated
   * values.
   */
  val type: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun ChatsRecord.toChat() =
  Chat(
    id = this.id!!,
    userId = this.userId,
    agentId = this.agentId,
    username = this.username,
    title = this.title,
    type = this.type,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )

@Serializable
data class UpdateChatTitleDTO(@CharRange(min = 1, max = 255) var title: String) : Validation
