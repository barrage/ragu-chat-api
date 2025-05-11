package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Email
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.SettingKey
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.types.KUUID

object BonvoyageWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var settings: Settings
  private lateinit var repository: BonvoyageRepository
  private lateinit var email: Email
  private lateinit var image: BlobStorage<Image>

  fun init(config: ApplicationConfig, state: ApplicationState) {
    providers = state.providers
    settings = state.settings
    repository = BonvoyageRepository(state.database)
    email = state.email
    image = state.providers.image
  }

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun new(user: User, emitter: Emitter, params: JsonElement?): Workflow {
    throw AppError.api(
      ErrorReason.InvalidOperation,
      "A Bonvoyage workflow can only be open from an approved travel request with `workflow.existing`.",
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    if (user.email == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "User email missing")
    }

    val trip = repository.getTrip(workflowId)

    val settings = settings.getAllWithDefaults()
    val bonvoyageLlmProvider = providers.llm[settings[SettingKey.BONVOYAGE_LLM_PROVIDER]]
    val bonvoyageModel = settings[SettingKey.BONVOYAGE_MODEL]
    return BonvoyageWorkflow(
      id = workflowId,
      emitter = emitter,
      bonvoyageExpenseAgent =
        BonvoyageExpenseAgent(
          user = user,
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(user, BONVOYAGE_WORKFLOW_ID, workflowId),
          model = bonvoyageModel,
          inferenceProvider = bonvoyageLlmProvider,
          trip = trip,
        ),
      repository = repository,
      email = email,
      image = image,
    )
  }
}
