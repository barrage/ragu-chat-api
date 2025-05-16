package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager

internal const val BONVOYAGE_WORKFLOW_ID = "BONVOYAGE"

class TripotronPlugin() : Plugin {
  private lateinit var admin: BonvoyageAdminApi
  private lateinit var user: BonvoyageUserApi

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    val repository = BonvoyageRepository(state.database)
    val scheduler = BonvoyageNotificationScheduler(state.email)

    val trips = repository.listTrips(pending = true)

    for (trip in trips) {
      scheduler.scheduleEmail(
        trip.userEmail,
        trip.startLocation,
        trip.endLocation,
        trip.startDateTime,
      )
    }

    scheduler.start()

    admin = BonvoyageAdminApi(repository, state.email, scheduler)
    user = BonvoyageUserApi(repository, state.email, state.providers.image)
    BonvoyageWorkflowFactory.init(config, state)
    WorkflowFactoryManager.register(BonvoyageWorkflowFactory)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    authenticate("admin") { bonvoyageAdminRoutes(admin) }
    authenticate("user") { bonvoyageUserRoutes(user) }
  }

  override fun RequestValidationConfig.configureRequestValidation() {
    validate<TravelRequest>(TravelRequest::validate)
    validate<BonvoyageStartTrip>(BonvoyageStartTrip::validate)
  }
}
