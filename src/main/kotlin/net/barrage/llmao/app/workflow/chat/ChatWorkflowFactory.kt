package net.barrage.llmao.app.workflow.chat

import com.knuddels.jtokkit.api.EncodingRegistry
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
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.tryUuid

private const val CHAT_TOKEN_ORIGIN = "workflow.chat"
private const val CHAT_WORKFLOW_ID = "CHAT"

class ChatWorkflowFactory(
  private val providers: ProviderState,
  private val services: Api,
  private val repository: RepositoryState,
  private val toolchainFactory: ToolchainFactory,
  private val settings: AdminSettingsService,
  private val encodingRegistry: EncodingRegistry,
  private val messageProcessor: ChatMessageProcessor,
  private val contextEnrichmentFactory: ContextEnrichmentFactory,
) : WorkflowFactory {
  private val chatRepositoryWrite: ChatRepositoryWrite = repository.chatWrite(CHAT_TOKEN_ORIGIN)
  private val chatRepositoryRead: ChatRepositoryRead = repository.chatRead(CHAT_TOKEN_ORIGIN)

  override fun type(): String = CHAT_WORKFLOW_ID

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
      messageProcessor = messageProcessor,
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
    emitter: Emitter? = null,
  ): ChatAgent {
    val tokenizer =
      encodingRegistry.getEncodingForModel(agent.configuration.model).let {
        if (it.isEmpty) null else it.get()
      }

    val settings = settings.getAllWithDefaults()

    val tokenTracker =
      TokenUsageTracker(
        repository = repository.tokenUsageW,
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
      toolchain = toolchainFactory.createAgentToolchain(agent.agent.id, emitter),
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
