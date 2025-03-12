package net.barrage.llmao.app.workflow.chat

import com.knuddels.jtokkit.api.EncodingRegistry
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.llm.ChatHistory
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.ToolchainFactory
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.Pagination
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingsService
import net.barrage.llmao.core.tokens.TokenUsageRepositoryWrite
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private const val CHAT_TOKEN_ORIGIN = "workflow.chat"

class ChatWorkflowFactory(
  private val providerState: ProviderState,
  private val agentService: AgentService,
  private val chatRepositoryWrite: ChatRepositoryWrite,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val toolchainFactory: ToolchainFactory,
  private val settingsService: SettingsService,
  private val tokenUsageRepositoryW: TokenUsageRepositoryWrite,
  private val encodingRegistry: EncodingRegistry,
) {
  suspend fun newChatWorkflow(
    user: User,
    agentId: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    val id = KUUID.randomUUID()
    // Throws if the agent does not exist or is inactive
    agentService.getActive(agentId)

    // TODO: Skip above check with single call
    val agent = agentService.getFull(agentId)
    val toolchain = toolchainFactory.createAgentToolchain(agentId, toolEmitter)
    val settings = settingsService.getAllWithDefaults()
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

    val chatAgent =
      agent.toChatAgent(
        history = history,
        providers = providerState,
        toolchain = toolchain,
        settings = settings,
        tokenTracker = tokenTracker,
      )

    val streamAgent = chatAgent.toStreaming(emitter)

    return ChatWorkflow(
      id = id,
      streamAgent = streamAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.New,
      user = user,
    )
  }

  suspend fun fromExistingChatWorkflow(
    user: User,
    id: KUUID,
    emitter: Emitter<ChatWorkflowMessage>,
    toolEmitter: Emitter<ToolEvent>? = null,
  ): ChatWorkflow {
    // TODO
    val chat =
      chatRepositoryRead.getWithMessages(id = id, userId = user.id, pagination = Pagination(1, 200))
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    agentService.getActive(chat.chat.agentId)

    val agent = agentService.getFull(chat.chat.agentId)

    val toolchain = toolchainFactory.createAgentToolchain(chat.chat.agentId, toolEmitter)
    val settings = settingsService.getAllWithDefaults()
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

    val messages = chat.messages.items.flatMap { it.messages }.map(ChatMessage::fromModel)

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

    val chatAgent =
      agent.toChatAgent(
        history = history,
        providers = providerState,
        toolchain = toolchain,
        settings = settings,
        tokenTracker = tokenTracker,
      )

    val streamAgent = chatAgent.toStreaming(emitter)

    return ChatWorkflow(
      id = chat.chat.id,
      user = user,
      streamAgent = streamAgent,
      emitter = emitter,
      repository = chatRepositoryWrite,
      state = ChatWorkflowState.Persisted(chat.chat.title!!),
    )
  }
}
