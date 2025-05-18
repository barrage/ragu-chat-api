package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.plugins.requestvalidation.RequestValidationConfig
import io.ktor.server.routing.Route
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager

internal const val BONVOYAGE_WORKFLOW_ID = "BONVOYAGE"

class BonvoyagePlugin() : Plugin {
  private lateinit var admin: BonvoyageAdminApi
  private lateinit var user: BonvoyageUserApi

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun initialize(config: ApplicationConfig, state: ApplicationState) {
    val repository = BonvoyageRepository(state.database)
    val scheduler = BonvoyageNotificationScheduler(state.email, repository)
    scheduler.start()

    admin = BonvoyageAdminApi(repository, state.email, state.settings, state.providers)
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
