package net.barrage.llmao.app.workflow.chat

import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.models.toChatAgent
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter

class ChatWorkflowFactory(
  private val providerState: ProviderState,
  private val agentService: AgentService,
  private val chatWorkflowRepository: ChatWorkflowRepository,
  private val toolchainFactory: ToolchainFactory,
) {
  suspend fun newChatWorkflow(
    userId: KUUID,
    agentId: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)

    // TODO: Skip above check with single call
    val agent = agentService.getFull(agentId)

    val chatAgent = agent.toChatAgent(providers = providerState)
    val toolchain = toolchainFactory.createAgentToolchain(agentId, toolEmitter)

    chatAgent.tools = toolchain?.listToolSchemas()

    return ChatWorkflow(
      id = id,
      userId = userId,
      agent = chatAgent,
      emitter = emitter,
      toolchain = toolchain,
      repository = chatWorkflowRepository,
      state = ChatWorkflowState.New,
    )
  }

  suspend fun fromExistingChatWorkflow(
    id: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    val chat = chatWorkflowRepository.getChatWithMessages(id)

    agentService.getActive(chat.chat.agentId)

    val agent = agentService.getFull(chat.chat.agentId)

    val chatAgent = agent.toChatAgent(providers = providerState)
    val toolchain = toolchainFactory.createAgentToolchain(chat.chat.agentId, toolEmitter)

    chatAgent.tools = toolchain?.listToolSchemas()

    val history = chat.messages.map(ChatMessage::fromModel)

    return ChatWorkflow(
      id = chat.chat.id,
      userId = chat.chat.userId,
      agent = chatAgent,
      history = history as MutableList<ChatMessage>,
      emitter = emitter,
      toolchain = toolchain,
      repository = chatWorkflowRepository,
      state = ChatWorkflowState.Persisted(chat.chat.title!!),
    )
  }
}
