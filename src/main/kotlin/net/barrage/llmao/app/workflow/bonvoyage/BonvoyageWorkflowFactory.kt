package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
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
    if (params == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "Missing trip input parameters")
    }

    if (user.email == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "User email missing")
    }

    val params = Json.decodeFromJsonElement(StartTrip.serializer(), params)
    val settings = settings.getAllWithDefaults()

    val workflowId = KUUID.randomUUID()
    val travelOrderId = requestTravelOrder(user)

    val tripotronLlmProvider = providers.llm[settings[SettingKey.BONVOYAGE_LLM_PROVIDER]]
    val tripotronModel = settings[SettingKey.BONVOYAGE_MODEL]

    repository.insertTrip(
      BonvoyageTripInsert(
        id = workflowId,
        trip =
          TripDetails(
            user = user,
            travelOrderId = travelOrderId,
            transportType = params.transportType,
            startLocation = params.startLocation,
            endLocation = params.endLocation,
            startDateTime = params.startDateTime,
            endDateTime = params.endDateTime,
            description = params.description,
            vehicleType = params.vehicleType,
            vehicleRegistration = params.vehicleRegistration,
            startMileage = params.startMileage,
            destination = params.destination,
          ),
      )
    )

    return BonvoyageWorkflow(
      id = workflowId,
      emitter = emitter,
      bonvoyage =
        Bonvoyage(
          user = user,
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(user, BONVOYAGE_WORKFLOW_ID, workflowId),
          model = tripotronModel,
          inferenceProvider = tripotronLlmProvider,
        ),
      repository = repository,
      email = email,
      image = image,
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    if (user.email == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "User email missing")
    }

    if (!repository.tripExists(workflowId)) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Trip does not exist")
    }

    val settings = settings.getAllWithDefaults()
    val bonvoyageLlmProvider = providers.llm[settings[SettingKey.BONVOYAGE_LLM_PROVIDER]]
    val bonvoyageModel = settings[SettingKey.BONVOYAGE_MODEL]
    return BonvoyageWorkflow(
      id = workflowId,
      emitter = emitter,
      bonvoyage =
        Bonvoyage(
          user = user,
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(user, BONVOYAGE_WORKFLOW_ID, workflowId),
          model = bonvoyageModel,
          inferenceProvider = bonvoyageLlmProvider,
        ),
      repository = repository,
      email = email,
      image = image,
    )
  }

  /** Returns the travel order ID. */
  suspend fun requestTravelOrder(user: User): String {
    // TODO: implement when we get BC
    return KUUID.randomUUID().toString()
  }
}
