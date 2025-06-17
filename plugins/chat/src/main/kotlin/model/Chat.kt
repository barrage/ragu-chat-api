import kotlinx.serialization.Serializable
import net.barrage.llmao.core.CharRange
import net.barrage.llmao.core.Validation
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.ChatsRecord

/** Base model with its messages. */
@Serializable
data class ChatWithMessages(val chat: Chat, val messages: CountedList<MessageGroupAggregate>)

@Serializable data class ChatWithAgent(val chat: Chat, val agent: Agent)

/** TABLE: chats */
@Serializable
data class Chat(
  val id: KUUID,

  /** Agent ID. */
  val agentId: KUUID,

  /** Agent configuration ID at time of chat creation. */
  val agentConfigurationId: KUUID,

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
    id = id!!,
    userId = userId,
    agentId = agentId,
    agentConfigurationId = agentConfigurationId,
    username = username,
    title = title,
    type = type,
    createdAt = createdAt!!,
    updatedAt = updatedAt!!,
  )

@Serializable
data class UpdateChatTitleDTO(@CharRange(min = 1, max = 255) var title: String) : Validation
