package net.barrage.llmao.app.workflow.bonvoyage

import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.llm.ChatMessageProcessor
import net.barrage.llmao.core.llm.MessageBasedHistory
import net.barrage.llmao.core.llm.TokenBasedHistory
import net.barrage.llmao.core.llm.ToolsBuilder
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.settings.Settings
import net.barrage.llmao.core.token.Encoder
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory

object BonvoyageWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var settings: Settings
  private lateinit var api: BonvoyageUserApi

  fun init(state: ApplicationState) {
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

    val settings = settings.getAll()

    val bonvoyageLlmProvider = providers.llm[settings[BonvoyageLlmProvider.KEY]]
    val bonvoyageModel = settings[BonvoyageModel.KEY]

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
          maxTokens =
            settings.getOptional(BonvoyageMaxHistoryTokens.KEY)?.toInt()
              ?: BonvoyageMaxHistoryTokens.DEFAULT,
        )
      } ?: MessageBasedHistory(messages = messages, maxMessages = 20)

    return BonvoyageWorkflow(
      id = workflowId,
      emitter = emitter,
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

  suspend fun expenseAgent(user: User, tripId: KUUID): BonvoyageExpenseAgent {
    val settings = settings.getAll()
    val bonvoyageLlmProvider = providers.llm[settings[BonvoyageLlmProvider.KEY]]
    val bonvoyageModel = settings[BonvoyageModel.KEY]

    return BonvoyageExpenseAgent(
      tokenTracker =
        TokenUsageTrackerFactory.newTracker(user.id, user.username, BONVOYAGE_WORKFLOW_ID, tripId),
      model = bonvoyageModel,
      inferenceProvider = bonvoyageLlmProvider,
    )
  }
}
