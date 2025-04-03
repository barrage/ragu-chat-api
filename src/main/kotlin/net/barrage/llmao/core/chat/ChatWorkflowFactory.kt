package net.barrage.llmao.core.chat

import com.knuddels.jtokkit.api.EncodingRegistry
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.agent.AgentService
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolchainFactory
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

class ChatWorkflowFactory(
  private val providerState: ProviderState,
  private val agentService: AgentService,
  private val chatRepositoryWrite: ChatRepositoryWrite,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val toolchainFactory: ToolchainFactory,
  private val settings: Settings,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  private val encodingRegistry: EncodingRegistry,
  private val messageProcessor: ChatMessageProcessor,
) {
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

    val toolchain = toolchainFactory.createAgentToolchain(agentId, toolEmitter)
    val settings = settings.getAllWithDefaults()
    val tokenTracker =
      TokenUsageTracker(
        repository = tokenUsageRepositoryW,
        userId = user.id,
        username = user.username,
        agentId = agentId,
        agentConfigurationId = agent.configuration.id,
        origin = CHAT_TOKEN_ORIGIN,
        originId = id,
      )

    val tokenizer = encodingRegistry.getEncodingForModel(agent.configuration.model)
    val history: ChatHistory =
      if (tokenizer.isEmpty) {
        MessageBasedHistory(messages = mutableListOf(), maxMessages = 20)
      } else {
        TokenBasedHistory(
          messages = mutableListOf(),
          tokenizer = tokenizer.get(),
          maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
        )
      }

    val completionParameters =
      ChatCompletionParameters(
        model = agent.configuration.model,
        temperature = agent.configuration.temperature,
        presencePenalty =
          agent.configuration.presencePenalty
            ?: settings[SettingKey.AGENT_PRESENCE_PENALTY].toDouble(),
        maxTokens =
          agent.configuration.maxCompletionTokens
            ?: settings.getOptional(SettingKey.AGENT_MAX_COMPLETION_TOKENS)?.toInt(),
        tools = toolchain?.listToolSchemas(),
      )

    val chatAgent =
      agent.toStreamingAgent(
        userId = user.id,
        allowedGroups = user.entitlements,
        history = history,
        providers = providerState,
        toolchain = toolchain,
        settings = settings,
        tokenTracker = tokenTracker,
        completionParameters = completionParameters,
        emitter = emitter,
      )

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

  suspend fun fromExistingChatWorkflow(
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

    val toolchain = toolchainFactory.createAgentToolchain(chat.chat.agentId, toolEmitter)
    val settings = settings.getAllWithDefaults()
    val tokenTracker =
      TokenUsageTracker(
        repository = tokenUsageRepositoryW,
        userId = chat.chat.userId,
        username = chat.chat.username,
        agentId = chat.chat.agentId,
        agentConfigurationId = agent.configuration.id,
        origin = CHAT_TOKEN_ORIGIN,
        originId = id,
      )

    val messages =
      chat.messages.items.flatMap { it.messages }.map(messageProcessor::loadToChatMessage)

    val tokenizer = encodingRegistry.getEncodingForModel(agent.configuration.model)
    val history: ChatHistory =
      if (tokenizer.isEmpty) {
        MessageBasedHistory(messages = messages.toMutableList(), maxMessages = 20)
      } else {
        TokenBasedHistory(
          messages = messages.toMutableList(),
          tokenizer = tokenizer.get(),
          maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
        )
      }

    val completionParameters =
      ChatCompletionParameters(
        model = agent.configuration.model,
        temperature = agent.configuration.temperature,
        presencePenalty =
          agent.configuration.presencePenalty
            ?: settings[SettingKey.AGENT_PRESENCE_PENALTY].toDouble(),
        maxTokens =
          agent.configuration.maxCompletionTokens
            ?: settings.getOptional(SettingKey.AGENT_MAX_COMPLETION_TOKENS)?.toInt(),
        tools = toolchain?.listToolSchemas(),
      )

    val chatAgent =
      agent.toStreamingAgent(
        userId = user.id,
        allowedGroups = user.entitlements,
        history = history,
        providers = providerState,
        toolchain = toolchain,
        settings = settings,
        tokenTracker = tokenTracker,
        completionParameters = completionParameters,
        emitter = emitter,
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
}
