package net.barrage.llmao.app.workflow.chat

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.app.workflow.chat.api.Api
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryRead
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryWrite
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.ContextEnrichmentFactory
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolFunctionDefinition
import net.barrage.llmao.core.llm.Tools
import net.barrage.llmao.core.llm.ToolsBuilder
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.types.KUUID

object ChatWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var api: Api
  private lateinit var agentRepository: AgentRepository
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
    settings = state.settings
    chatRepositoryRead = chatRead
    chatRepositoryWrite = chatWrite
  }

  override fun id(): String = CHAT_WORKFLOW_ID

  override suspend fun new(user: User, emitter: Emitter, params: JsonElement?): Workflow {
    if (params == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "Missing agent ID in creation parameters")
    }

    val agentId = Json.decodeFromJsonElement(NewChatWorkflow.serializer(), params).agentId
    val id = KUUID.randomUUID()

    val agent =
      if (user.isAdmin()) {
        // Load it regardless of active status
        api.admin.agent.getFull(agentId)
      } else {
        api.user.agent.getFull(agentId, user.entitlements)
      }

    val chatAgent = createChatAgent(id, user.id, user.username, user.entitlements, agent)

    return ChatWorkflow(
      id = id,
      agent = chatAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.New,
      user = user,
      tools = loadAgentTools(agent.agent.id),
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

    val chatAgent = createChatAgent(workflowId, user.id, user.username, user.entitlements, agent)

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
      tools = loadAgentTools(agent.agent.id),
    )
  }

  suspend fun createChatAgent(
    workflowId: KUUID,
    userId: String,
    username: String,
    entitlements: List<String>,
    agent: AgentFull,
  ): ChatAgent {
    val tokenizer = Encoder.tokenizer(agent.configuration.model)

    val settings = settings.getAll()

    val tokenTracker =
      TokenUsageTrackerFactory.newTracker(userId, username, CHAT_WORKFLOW_ID, workflowId)

    val contextEnrichment =
      ContextEnrichmentFactory.collectionEnrichment(
        userEntitlements = entitlements,
        tokenTracker = tokenTracker,
        collections = agent.collections,
      )

    return ChatAgent(
      agentId = agent.agent.id,
      configuration = agent.configuration,
      name = agent.agent.name,
      titleMaxTokens =
        settings.getOptional(AgentTitleMaxCompletionTokens.KEY)?.toInt()
          ?: AgentTitleMaxCompletionTokens.DEFAULT,
      inferenceProvider = providers.llm[agent.configuration.llmProvider],
      completionParameters =
        ChatCompletionBaseParameters(
          model = agent.configuration.model,
          temperature = agent.configuration.temperature,
          presencePenalty =
            agent.configuration.presencePenalty
              ?: settings.getOptional(AgentPresencePenalty.KEY)?.toDouble(),
          maxTokens = agent.configuration.maxCompletionTokens,
        ),
      tokenTracker = tokenTracker,
      history =
        tokenizer?.let {
          TokenBasedHistory(
            messages = mutableListOf(),
            tokenizer = it,
            maxTokens =
              settings.getOptional(MaxHistoryTokens.KEY)?.toInt() ?: MaxHistoryTokens.DEFAULT,
          )
        } ?: MessageBasedHistory(messages = mutableListOf(), maxMessages = 20),
      contextEnrichment = contextEnrichment?.let { listOf(it) },
    )
  }

  private suspend fun loadAgentTools(agentId: KUUID): Tools? {
    val agentTools = agentRepository.getAgentTools(agentId).map { it.toolName }

    if (agentTools.isEmpty()) {
      return null
    }

    val toolchain = ToolsBuilder()

    for (tool in agentTools) {
      val definition = ChatToolExecutor.getToolDefinition(tool)
      if (definition == null) {
        LOG.warn("Attempted to load tool '$tool' but it does not exist in the tool registry")
        continue
      }

      val handler = ChatToolExecutor.getToolFunction(tool)
      if (handler == null) {
        LOG.warn("Attempted to load tool '$tool' but it does not have a handler")
        continue
      }

      toolchain.addTool(definition, handler)
    }
    val tools = toolchain.build()

    LOG.info(
      "Loading toolchain for '{}', available tools: {}",
      agentId,
      tools.listToolSchemas().map(ToolDefinition::function).map(ToolFunctionDefinition::name),
    )

    return toolchain.build()
  }
}

@Serializable data class NewChatWorkflow(val agentId: KUUID)
