package net.barrage.llmao.core.chat

import com.knuddels.jtokkit.api.EncodingRegistry
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.agent.AgentService
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.token.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter

private const val CHAT_TOKEN_ORIGIN = "workflow.chat"

class WorkflowFactory(
  private val providerState: ProviderState,
  private val agentService: AgentService,
  private val chatRepositoryWrite: ChatRepositoryWrite,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val toolchainFactory: ToolchainFactory,
  private val settings: Settings,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  private val encodingRegistry: EncodingRegistry,
  private val messageProcessor: ChatMessageProcessor,
  private val contextEnrichmentFactory: ContextEnrichmentFactory,
) {
  suspend fun newWorkflow(
    user: User,
    agentId: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    return newChatWorkflow(user, agentId, emitter, toolEmitter)
  }

  suspend fun newChatWorkflow(
    user: User,
    agentId: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    val id = KUUID.randomUUID()
    val agent =
      if (user.isAdmin()) {
        // Load it regardless of active status
        agentService.getFull(agentId)
      } else {
        agentService.userGetFull(agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(id, user, agent, emitter, toolEmitter)

    return ChatWorkflow(
      id = id,
      agent = chatAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.New,
      user = user,
      messageProcessor = messageProcessor,
    )
  }

  suspend fun existingChatWorkflow(
    user: User,
    id: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    // TODO: Pagination
    val chat =
      chatRepositoryRead.getWithMessages(id = id, userId = user.id, pagination = Pagination(1, 200))
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent =
      if (user.isAdmin()) agentService.getFull(chat.chat.agentId)
      else {
        agentService.userGetFull(chat.chat.agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(id, user, agent, emitter, toolEmitter)

    chatAgent.addToHistory(
      chat.messages.items.flatMap { it.messages }.map(messageProcessor::loadToChatMessage)
    )

    return ChatWorkflow(
      id = chat.chat.id,
      user = user,
      agent = chatAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.Persisted(chat.chat.title!!),
      messageProcessor = messageProcessor,
    )
  }

  suspend fun createChatAgent(
    workflowId: KUUID,
    user: User,
    agent: AgentFull,
    emitter: Emitter<ChatWorkflowMessage>? = null,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatAgent {
    val tokenizer =
      encodingRegistry.getEncodingForModel(agent.configuration.model).let {
        if (it.isEmpty) null else it.get()
      }

    val settings = settings.getAllWithDefaults()

    val tokenTracker =
      TokenUsageTracker(
        repository = tokenUsageRepositoryW,
        user = user,
        agentId = agent.agent.id,
        originType = CHAT_TOKEN_ORIGIN,
        originId = workflowId,
      )

    val contextEnrichment =
      contextEnrichmentFactory.collectionEnrichment(
        user = user,
        tokenTracker = tokenTracker,
        collections = agent.collections,
      )

    return ChatAgent(
      agentId = agent.agent.id,
      configurationId = agent.configuration.id,
      name = agent.agent.name,
      instructions = agent.configuration.agentInstructions,
      titleMaxTokens = settings[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt(),
      user = user,
      model = agent.configuration.model,
      llm = providerState.llm.getProvider(agent.configuration.llmProvider),
      context = agent.configuration.context,
      completionParameters =
        ChatCompletionParameters(
          model = agent.configuration.model,
          temperature = agent.configuration.temperature,
          presencePenalty =
            agent.configuration.presencePenalty
              ?: settings[SettingKey.AGENT_PRESENCE_PENALTY].toDouble(),
          maxTokens =
            agent.configuration.maxCompletionTokens
              ?: settings.getOptional(SettingKey.AGENT_MAX_COMPLETION_TOKENS)?.toInt(),
          // Tools is a dynamic property that will get set during inference, if the agent has them
          tools = null,
        ),
      toolchain = toolchainFactory.createAgentToolchain(agent.agent.id, toolEmitter),
      tokenTracker = tokenTracker,
      history =
        tokenizer?.let {
          TokenBasedHistory(
            messages = mutableListOf(),
            tokenizer = it,
            maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
          )
        } ?: MessageBasedHistory(messages = mutableListOf(), maxMessages = 20),
      attachmentProcessor = messageProcessor,
      emitter = emitter,
      contextEnrichment = contextEnrichment?.let { listOf(it) },
    )
  }
}
