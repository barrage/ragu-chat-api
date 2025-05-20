package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.SettingKey
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.llm.ToolsBuilder
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.types.KUUID

object BonvoyageWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var settings: Settings
  private lateinit var api: BonvoyageUserApi

  fun init(config: ApplicationConfig, state: ApplicationState) {
    providers = state.providers
    settings = state.settings
    api = BonvoyageUserApi(BonvoyageRepository(state.database), state.email, state.providers.image)
  }

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun new(user: User, emitter: Emitter, params: JsonElement?): Workflow {
    throw AppError.api(
      ErrorReason.InvalidOperation,
      "A Bonvoyage workflow can only be opened from an approved travel request with `workflow.existing`.",
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    api.getTrip(workflowId, user.id)

    val settings = settings.getAllWithDefaults()
    val bonvoyageLlmProvider = providers.llm[settings[SettingKey.BONVOYAGE_LLM_PROVIDER]]
    val bonvoyageModel = settings[SettingKey.BONVOYAGE_MODEL]

    val tokenizer = Encoder.tokenizer(bonvoyageModel)
    val messages =
      api
        .getTripChatMessages(workflowId)
        .flatMap { it.messages.map(ChatMessageProcessor::loadToChatMessage) }
        .toMutableList()
    val history =
      tokenizer?.let {
        TokenBasedHistory(
          messages = messages,
          tokenizer = it,
          maxTokens = settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt(),
        )
      } ?: MessageBasedHistory(messages = messages, maxMessages = 20)

    return BonvoyageWorkflow(
      id = workflowId,
      emitter = emitter,
      expenseAgent =
        BonvoyageExpenseAgent(
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(
              user.id,
              user.username,
              BONVOYAGE_WORKFLOW_ID,
              workflowId,
            ),
          model = bonvoyageModel,
          inferenceProvider = bonvoyageLlmProvider,
        ),
      chatAgent =
        BonvoyageChatAgent(
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(
              user.id,
              user.username,
              BONVOYAGE_WORKFLOW_ID,
              workflowId,
            ),
          model = bonvoyageModel,
          inferenceProvider = bonvoyageLlmProvider,
          history = history,
        ),
      user = user,
      api = api,
      tools = ToolsBuilder().build(),
    )
  }
}
