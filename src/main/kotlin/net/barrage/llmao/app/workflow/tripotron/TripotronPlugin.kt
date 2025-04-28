package net.barrage.llmao.app.workflow.tripotron

import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager

internal const val TRIPOTRON_WORKFLOW_ID = "TRIPOTRON"

class TripotronPlugin() : Plugin {
  private lateinit var repository: TripotronRepository

  override fun id(): String = TRIPOTRON_WORKFLOW_ID

  override suspend fun configure(config: ApplicationConfig, state: ApplicationState) {
    repository = TripotronRepository(state.database)
    TripotronWorkflowFactory.init(config, state)
    WorkflowFactoryManager.register(TripotronWorkflowFactory)
  }

  override fun Route.routes(state: ApplicationState) {
    route("/tripotron/trips") { get { call.respond(repository.listTrips()) } }
  }
}
