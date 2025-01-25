package net.barrage.llmao.core.session

import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.ConversationService
import net.barrage.llmao.core.session.chat.ChatSession
import net.barrage.llmao.core.session.chat.ChatSessionMessage
import net.barrage.llmao.core.types.KUUID

class SessionFactory(
  private val agentService: AgentService,
  private val conversationService: ConversationService,
) {

  suspend fun newChatSession(
    userId: KUUID,
    agentId: KUUID,
    emitter: Emitter<ChatSessionMessage>,
  ): ChatSession {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)
    return ChatSession(
      id = id,
      userId = userId,
      agentId = agentId,
      service = conversationService,
      emitter = emitter,
    )
  }

  suspend fun fromExistingChat(
    id: KUUID,
    emitter: Emitter<ChatSessionMessage>,
    initialHistorySize: Int,
  ): ChatSession {
    val chat = conversationService.getChat(id, Pagination(1, initialHistorySize))

    agentService.getActive(chat.chat.agentId)

    val history = chat.messages.map(ChatMessage::fromModel)

    return ChatSession(
      id = chat.chat.id,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      service = conversationService,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      emitter = emitter,
    )
  }
}
