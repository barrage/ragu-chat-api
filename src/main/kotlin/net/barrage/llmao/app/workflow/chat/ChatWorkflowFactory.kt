package net.barrage.llmao.app.workflow.chat

import io.ktor.server.config.ApplicationConfig
import net.barrage.llmao.app.ApplicationState
import net.barrage.llmao.app.CHAT_WORKFLOW_ID
import net.barrage.llmao.core.Api
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.RepositoryState
import net.barrage.llmao.core.api.admin.AdminSettingsService
import net.barrage.llmao.core.api.admin.SettingKey
import net.barrage.llmao.core.chat.ChatAgent
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.chat.MessageBasedHistory
import net.barrage.llmao.core.chat.TokenBasedHistory
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.llm.ToolRegistry
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.llm.ToolchainBuilder
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.LOG
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.tryUuid

object ChatWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var services: Api
  private lateinit var repository: RepositoryState
  private lateinit var settings: AdminSettingsService
  private lateinit var chatRepositoryWrite: ChatRepositoryWrite
  private lateinit var chatRepositoryRead: ChatRepositoryRead

  override fun id(): String = CHAT_WORKFLOW_ID

  override suspend fun init(config: ApplicationConfig, state: ApplicationState) {
    providers = state.providers
    services = state.services
    repository = state.repository
    settings = services.admin.settings
    chatRepositoryWrite = repository.chatWrite(CHAT_WORKFLOW_ID)
    chatRepositoryRead = repository.chatRead(CHAT_WORKFLOW_ID)
  }

  override suspend fun new(user: User, agentId: String?, emitter: Emitter): Workflow {
    if (agentId == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "Missing agentId")
    }
    val id = KUUID.randomUUID()
    val agentId = tryUuid(agentId)
    val agent =
      if (user.isAdmin()) {
        // Load it regardless of active status
        services.admin.agent.getFull(agentId)
      } else {
        services.user.agent.getFull(agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(id, user, agent, emitter)

    return ChatWorkflow(
      id = id,
      agent = chatAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.New,
      user = user,
    )
  }

  override suspend fun existing(user: User, id: KUUID, emitter: Emitter): Workflow {
    // TODO: Pagination
    val chat =
      chatRepositoryRead.getWithMessages(id = id, userId = user.id, pagination = Pagination(1, 200))
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val agent =
      if (user.isAdmin()) services.admin.agent.getFull(chat.chat.agentId)
      else {
        services.user.agent.getFull(chat.chat.agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(id, user, agent, emitter)

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
    )
  }

  suspend fun createChatAgent(
    workflowId: KUUID,
    user: User,
    agent: AgentFull,
    emitter: Emitter? = null,
  ): ChatAgent {

    val tokenizer = Encoder.tokenizer(agent.configuration.model)

    val settings = settings.getAllWithDefaults()

    val tokenTracker =
      TokenUsageTracker(
        repository = repository.tokenUsageW,
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
      llmProvider = providers.llm.getProvider(agent.configuration.llmProvider),
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
      toolchain = loadAgentTools(agent.agent.id, emitter),
      tokenTracker = tokenTracker,
      history =
        tokenizer?.let {
          TokenBasedHistory(
            messages = mutableListOf(),
            tokenizer = it,
            maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
          )
        } ?: MessageBasedHistory(messages = mutableListOf(), maxMessages = 20),
      emitter = emitter,
      contextEnrichment = contextEnrichment?.let { listOf(it) },
    )
  }

  private suspend fun loadAgentTools(agentId: KUUID, emitter: Emitter? = null): Toolchain<Api>? {
    val agentTools = repository.agent.getAgentTools(agentId).map { it.toolName }

    if (agentTools.isEmpty()) {
      return null
    }

    if (emitter == null) {
      LOG.warn("Building toolchain without an emitter; Realtime tool call events will not be sent.")
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

    LOG.info(
      "Loading toolchain for '{}', available tools: {}",
      agentId,
      toolchain.listToolNames().joinToString(", "),
    )

    return toolchain.build(services, emitter)
  }
}
