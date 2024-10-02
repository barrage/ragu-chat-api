package net.barrage.llmao.app.api.ws

import net.barrage.llmao.ProviderState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.PromptFormatter
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.apiError

class ChatFactory(
  private val providers: ProviderState,
  private val agentService: AgentService,
  private val chatService: ChatService,
) {

  fun new(userId: KUUID, agentId: KUUID): Chat {
    val agent = agentService.get(agentId)

    // TODO: Move this to agent creation/update routes
    // val llm = providers.llm.getProvider(agent.llmProvider)
    // if (!llm.supportsModel(agent.model)) {
    //  throw apiError(
    //    "Invalid Model",
    //    "Provider '${llm.id()}' does not support model '${agent.model}'",
    //  )
    // }

    val id = KUUID.randomUUID()

    return Chat(
      chatService,
      id = id,
      userId = userId,
      agentId = agentId,
      formatter = PromptFormatter(agent.context, agent.language),
    )
  }

  fun fromExisting(id: KUUID): Chat {
    val chat =
      chatService.getChat(id) ?: throw apiError("Entity does not exist", "Chat with ID '$id'")

    val agent = agentService.get(chat.chat.agentId)

    val history = chat.messages.map { ChatMessage(it.senderType, it.content, it.responseTo) }

    return Chat(
      chatService,
      id = chat.chat.id,
      userId = chat.chat.userId,
      agentId = chat.chat.agentId,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      formatter = PromptFormatter(agent.context, agent.language),
    )
  }
}
