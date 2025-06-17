import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.types.KUUID

/** API designed for end-user operations. Restricts access to chats owned by the user. */
class PublicChatService(
  private val chats: ChatRepositoryRead,
  private val agents: AgentRepository,
) {
  suspend fun listChats(pagination: PaginationSort, userId: String): CountedList<Chat> {
    return chats.getAll(pagination, userId)
  }

  suspend fun getChatWithAgent(id: KUUID, userId: String): ChatWithAgent {
    val chat = chats.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    if (userId != chat.userId) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    val agent =
      agents.getAgent(chat.agentId)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

    return ChatWithAgent(chat, agent)
  }

  suspend fun getChat(chatId: KUUID, userId: String): Chat {
    return chats.get(chatId, userId)
      ?: throw AppError.api(
        ErrorReason.EntityDoesNotExist,
        "Chat with ID '$chatId' for user '$userId'",
      )
  }

  suspend fun userUpdateTitle(chatId: KUUID, userId: String, title: String): Chat {
    return chats.userUpdateTitle(chatId, userId, title)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  suspend fun deleteChat(id: KUUID, userId: String) {
    chats.delete(id, userId)
  }

  suspend fun evaluateMessage(
    chatId: KUUID,
    messageGroupId: KUUID,
    userId: String,
    input: EvaluateMessage,
  ) {
    chats.get(chatId, userId)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")

    val amount = chats.evaluateMessageGroup(messageGroupId, input)
    if (amount == 0) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Message not found")
    }
  }

  suspend fun getMessages(
    id: KUUID,
    userId: String,
    pagination: Pagination,
  ): CountedList<MessageGroupAggregate> {
    chats.get(id, userId) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val messages = chats.getWorkflowMessages(workflowId = id, pagination = pagination)

    if (messages.total == 0) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
    }

    return messages
  }
}
