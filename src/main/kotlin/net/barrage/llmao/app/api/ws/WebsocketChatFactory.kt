package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.ConversationService
import net.barrage.llmao.core.types.KUUID

class WebsocketChatFactory(
  private val agentService: AgentService,
  private val conversationService: ConversationService,
) {

  suspend fun new(userId: KUUID, agentId: KUUID, channel: WebsocketChannel): Chat {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)
    val maxHistory = conversationService.getChatMaxHistory()

    return Chat(
      id = id,
      userId = userId,
      agentId = agentId,
      service = conversationService,
      channel = channel,
      maxHistory = maxHistory,
    )
  }

  suspend fun fromExisting(id: KUUID, channel: WebsocketChannel, initialHistorySize: Int): Chat {
    val chat = conversationService.getChat(id, Pagination(1, initialHistorySize))

    agentService.getActive(chat.chat.agentId)

    val history = chat.messages.map(ChatMessage::fromModel)
    val maxHistory = conversationService.getChatMaxHistory()

    return Chat(
      id = chat.chat.id,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      service = conversationService,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      channel = channel,
      maxHistory = maxHistory,
    )
  }
}
