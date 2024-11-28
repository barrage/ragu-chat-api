package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.types.KUUID

class ChatFactory(private val agentService: AgentService, private val chatService: ChatService) {

  fun new(userId: KUUID, agentId: KUUID, channel: Channel): Chat {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)
    return Chat(
      id = id,
      service = chatService,
      userId = userId,
      agentId = agentId,
      channel = channel,
    )
  }

  fun fromExisting(id: KUUID, channel: Channel): Chat {
    val chat = chatService.getChat(id)

    agentService.getActive(chat.chat.agentId)

    val history = chat.messages.map(ChatMessage::fromModel)

    return Chat(
      id = chat.chat.id,
      service = chatService,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      channel = channel,
    )
  }
}
