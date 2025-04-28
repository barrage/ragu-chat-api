package net.barrage.llmao.app.workflow.tripotron

import io.ktor.server.config.ApplicationConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.administration.settings.SettingKey
import net.barrage.llmao.core.administration.settings.Settings
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.repository.SpecialistRepositoryWrite
import net.barrage.llmao.core.token.TokenUsageTrackerFactory
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowFactory
import net.barrage.llmao.types.KUUID

object TripotronWorkflowFactory : WorkflowFactory {
  private lateinit var providers: ProviderState
  private lateinit var settings: Settings
  private lateinit var repository: TripotronRepository
  private lateinit var tripotronWrite: SpecialistRepositoryWrite

  fun init(config: ApplicationConfig, state: ApplicationState) {
    providers = state.providers
    settings = state.settings
    repository = TripotronRepository(state.database)
    tripotronWrite = SpecialistRepositoryWrite(state.database, TRIPOTRON_WORKFLOW_ID)
  }

  override suspend fun new(user: User, emitter: Emitter, params: JsonElement?): Workflow {
    if (params == null) {
      throw AppError.api(ErrorReason.InvalidParameter, "Missing trip input parameters")
    }
    val params = Json.decodeFromJsonElement(StartTrip.serializer(), params)
    val settings = settings.getAllWithDefaults()

    val workflowId = KUUID.randomUUID()
    val travelOrderId = requestTravelOrder(user)

    val tripotronLlmProvider = providers.llm[settings[SettingKey.TRIPOTRON_LLM_PROVIDER]]
    val tripotronModel = settings[SettingKey.TRIPOTRON_MODEL]

    repository.insertTrip(
      TripotronInsertTrip(
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
          ),
      )
    )

    return TripotronWorkflow(
      id = workflowId,
      state = TripotronWorkflowState.Started,
      emitter = emitter,
      tripotron =
        Tripotron(
          user = user,
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(user, TRIPOTRON_WORKFLOW_ID, workflowId),
          model = tripotronModel,
          inferenceProvider = tripotronLlmProvider,
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
            ),
          tools = null,
        ),
      tripotronWrite = tripotronWrite,
      repository = repository,
    )
  }

  override suspend fun existing(user: User, workflowId: KUUID, emitter: Emitter): Workflow {
    val trip = repository.getTrip(workflowId) ?: throw AppError.api(ErrorReason.EntityDoesNotExist)
    val settings = settings.getAllWithDefaults()
    val tripotronLlmProvider = providers.llm[settings[SettingKey.TRIPOTRON_LLM_PROVIDER]]
    val tripotronModel = settings[SettingKey.TRIPOTRON_MODEL]
    return TripotronWorkflow(
      id = workflowId,
      state = TripotronWorkflowState.Started,
      emitter = emitter,
      tripotron =
        Tripotron(
          user = user,
          tokenTracker =
            TokenUsageTrackerFactory.newTracker(user, TRIPOTRON_WORKFLOW_ID, workflowId),
          model = tripotronModel,
          inferenceProvider = tripotronLlmProvider,
          trip =
            TripDetails(
              user = user,
              travelOrderId = trip.travelOrderId,
              transportType = trip.transportType,
              startLocation = trip.startLocation,
              endLocation = trip.endLocation,
              startDateTime = trip.startDateTime,
              endDateTime = trip.endDateTime,
              description = trip.description,
              vehicleType = trip.vehicleType,
              vehicleRegistration = trip.vehicleRegistration,
              startMileage = trip.startMileage,
              endMileage = trip.endMileage,
            ),
          tools = null,
        ),
      tripotronWrite = tripotronWrite,
      repository = repository,
    )
  }

  override fun id(): String = TRIPOTRON_WORKFLOW_ID

  /** Returns the travel order ID. */
  suspend fun requestTravelOrder(user: User): String {
    // TODO: implement when we get BC
    return KUUID.randomUUID().toString()
  }
}
