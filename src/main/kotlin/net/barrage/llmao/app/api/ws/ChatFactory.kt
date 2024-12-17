package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.ConversationService
import net.barrage.llmao.core.types.KUUID

class ChatFactory(
  private val agentService: AgentService,
  private val conversationService: ConversationService,
) {

  fun new(userId: KUUID, agentId: KUUID, channel: Channel): Chat {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)
    return Chat(
      id = id,
      service = conversationService,
      userId = userId,
      agentId = agentId,
      channel = channel,
    )
  }

  suspend fun fromExisting(id: KUUID, channel: Channel, initialHistorySize: Int): Chat {
    val chat = conversationService.getChat(id, Pagination(1, initialHistorySize))

    agentService.getActive(chat.chat.agentId)

    val history = chat.messages.map(ChatMessage::fromModel)

    return Chat(
      id = chat.chat.id,
      service = conversationService,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      channel = channel,
    )
  }
}
