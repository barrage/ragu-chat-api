package net.barrage.llmao.app.api.ws

import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.PromptFormatter
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class ChatFactory(private val agentService: AgentService, private val chatService: ChatService) {

  fun new(userId: KUUID, agentId: KUUID): Chat {
    val agentFull = agentService.get(agentId)

    val id = KUUID.randomUUID()

    return Chat(
      chatService,
      id = id,
      userId = userId,
      agentId = agentId,
      formatter = PromptFormatter(agentFull.agent.context, agentFull.agent.language),
    )
  }

  fun fromExisting(id: KUUID): Chat {
    val chat =
      chatService.getChat(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat with ID '$id'")

    val agentFull = agentService.get(chat.chat.agentId)

    val history = chat.messages.map(ChatMessage::fromModel)

    return Chat(
      chatService,
      id = chat.chat.id,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      formatter = PromptFormatter(agentFull.agent.context, agentFull.agent.language),
    )
  }
}
