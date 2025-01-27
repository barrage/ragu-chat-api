package net.barrage.llmao.core.session

import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.models.toSessionAgent
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.session.chat.ChatSession
import net.barrage.llmao.core.session.chat.ChatSessionAgent
import net.barrage.llmao.core.session.chat.ChatSessionMessage
import net.barrage.llmao.core.session.chat.ChatSessionRepository
import net.barrage.llmao.core.types.KUUID

class SessionFactory(
  private val providerState: ProviderState,
  private val agentService: AgentService,
  private val chatSessionRepository: ChatSessionRepository,
  private val toolchainFactory: ToolchainFactory,
) {

  suspend fun newChatSession(
    userId: KUUID,
    agentId: KUUID,
    emitter: Emitter<ChatSessionMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatSession {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)

    // TODO: Skip above check with single call
    val agent = agentService.getFull(agentId)
    val sessionAgent = agent.toSessionAgent()
    val chatSessionAgent = ChatSessionAgent(providerState, sessionAgent)

    val toolchain = toolchainFactory.createAgentToolchain(agentId, toolEmitter)

    return ChatSession(
      id = id,
      userId = userId,
      sessionAgent = chatSessionAgent,
      emitter = emitter,
      toolchain = toolchain,
      repository = chatSessionRepository,
    )
  }

  suspend fun fromExistingChat(
    id: KUUID,
    emitter: Emitter<ChatSessionMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
    initialHistorySize: Int,
  ): ChatSession {
    val chat = chatSessionRepository.getChatWithMessages(id, initialHistorySize)

    agentService.getActive(chat.chat.agentId)

    val agent = agentService.getFull(chat.chat.agentId)
    val sessionAgent = agent.toSessionAgent()
    val chatSessionAgent = ChatSessionAgent(providerState, sessionAgent)

    val toolchain = toolchainFactory.createAgentToolchain(chat.chat.agentId, toolEmitter)

    val history = chat.messages.map(ChatMessage::fromModel)

    return ChatSession(
      id = chat.chat.id,
      userId = chat.chat.userId,
      sessionAgent = chatSessionAgent,
      title = chat.chat.title,
      messageReceived = history.isNotEmpty(),
      history = history as MutableList<ChatMessage>,
      emitter = emitter,
      toolchain = toolchain,
      repository = chatSessionRepository,
    )
  }
}
