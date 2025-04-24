package net.barrage.llmao.app.workflow.chat

import net.barrage.llmao.app.workflow.chat.api.Api
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryRead
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.SettingKey
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.MessageBasedHistory
import net.barrage.llmao.core.chat.TokenBasedHistory
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolFunctionDefinition
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.llm.ToolchainBuilder
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.repository.TokenUsageRepositoryWrite
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.tryUuid
import net.barrage.llmao.types.KUUID

object ChatWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var api: Api
  private lateinit var agentRepository: AgentRepository
  private lateinit var tokenUsageWrite: TokenUsageRepositoryWrite
  private lateinit var settings: Settings
  private lateinit var chatRepositoryRead: ChatRepositoryRead
  private lateinit var chatRepositoryWrite: ChatRepositoryWrite

  fun init(providers: ProviderState, api: Api, state: ApplicationState) {
    val chatRead = ChatRepositoryRead(state.database, CHAT_WORKFLOW_ID)
    val chatWrite = ChatRepositoryWrite(state.database, CHAT_WORKFLOW_ID)
    val agentRepository = AgentRepository(state.database)

    this.providers = providers
    this.api = api
    this.agentRepository = agentRepository
    tokenUsageWrite = state.tokenUsageWrite
    settings = state.settings
    chatRepositoryRead = chatRead
    chatRepositoryWrite = chatWrite
  }

  override fun id(): String = CHAT_WORKFLOW_ID

  override suspend fun new(user: User, agentId: String?, emitter: Emitter): Workflow {
    if (agentId == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "Missing agentId")
    }
    val id = KUUID.randomUUID()
    val agentId = tryUuid(agentId)
    val agent =
      if (user.isAdmin()) {
        // Load it regardless of active status
        api.admin.agent.getFull(agentId)
      } else {
        api.user.agent.getFull(agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(id, user, agent)

    return ChatWorkflow(
      id = id,
      agent = chatAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.New,
      user = user,
      toolchain = loadAgentTools(agent.agent.id),
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    // TODO: Pagination
    val chat =
      chatRepositoryRead.getWithMessages(
        id = workflowId,
        userId = user.id,
        pagination = Pagination(1, 200),
      ) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent =
      if (user.isAdmin()) api.admin.agent.getFull(chat.chat.agentId)
      else {
        api.user.agent.getFull(chat.chat.agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(workflowId, user, agent)

    chatAgent.addToHistory(
      chat.messages.items.flatMap { it.messages }.map(ChatMessageProcessor::loadToChatMessage)
    )

    return ChatWorkflow(
      id = chat.chat.id,
      user = user,
      agent = chatAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.Persisted(chat.chat.title!!),
      toolchain = loadAgentTools(agent.agent.id),
    )
  }

  suspend fun createChatAgent(workflowId: KUUID, user: User, agent: AgentFull): ChatAgent {
    val tokenizer = Encoder.tokenizer(agent.configuration.model)

    val settings = settings.getAllWithDefaults()

    val tokenTracker =
      TokenUsageTracker(
        repository = tokenUsageWrite,
        user = user,
        agentId = agent.agent.id,
        originType = CHAT_WORKFLOW_ID,
        originId = workflowId,
      )

    val contextEnrichment =
      ContextEnrichmentFactory.collectionEnrichment(
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
      inferenceProvider = providers.llm[agent.configuration.llmProvider],
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
      tokenTracker = tokenTracker,
      history =
        tokenizer?.let {
          TokenBasedHistory(
            messages = mutableListOf(),
            tokenizer = it,
            maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
          )
        } ?: MessageBasedHistory(messages = mutableListOf(), maxMessages = 20),
      contextEnrichment = contextEnrichment?.let { listOf(it) },
      tools = loadAgentTools(agent.agent.id)?.listToolSchemas(),
    )
  }

  private suspend fun loadAgentTools(agentId: KUUID): Toolchain<Api>? {
    val agentTools = agentRepository.getAgentTools(agentId).map { it.toolName }

    if (agentTools.isEmpty()) {
      return null
    }

    val toolchain = ToolchainBuilder<Api>()

    for (tool in agentTools) {
      val definition = ToolRegistry.getToolDefinition(tool)
      if (definition == null) {
        LOG.warn("Attempted to load tool '$tool' but it does not exist in the tool registry")
        continue
      }

      val handler = ToolRegistry.getToolFunction(tool)
      if (handler == null) {
        LOG.warn("Attempted to load tool '$tool' but it does not have a handler")
        continue
      }

      toolchain.addTool(definition, handler)
    }
    val tools = toolchain.build(api)

    LOG.info(
      "Loading toolchain for '{}', available tools: {}",
      agentId,
      tools.listToolSchemas().map(ToolDefinition::function).map(ToolFunctionDefinition::name),
    )

    return toolchain.build(api)
  }
}
