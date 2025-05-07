package net.barrage.llmao.app.workflow.bonvoyage

import io.ktor.server.auth.authenticate
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import net.barrage.llmao.app.http.pathUuid
import net.barrage.llmao.app.http.user
import net.barrage.llmao.core.ApplicationState
import net.barrage.llmao.core.Plugin
import net.barrage.llmao.core.workflow.WorkflowFactoryManager

internal const val BONVOYAGE_WORKFLOW_ID = "BONVOYAGE"

class TripotronPlugin() : Plugin {
  private lateinit var repository: BonvoyageRepository

  override fun id(): String = BONVOYAGE_WORKFLOW_ID

  override suspend fun configureState(config: ApplicationConfig, state: ApplicationState) {
    repository = BonvoyageRepository(state.database)
    BonvoyageWorkflowFactory.init(config, state)
    WorkflowFactoryManager.register(BonvoyageWorkflowFactory)
  }

  override fun Route.configureRoutes(state: ApplicationState) {
    authenticate("admin") {
      route("/admin/bonvoyage/trips") {
        get { call.respond(repository.listTrips()) }
        get("/{id}") {
          val id = call.pathUuid("id")
          val trip = repository.getTripAggregate(id)
          call.respond(trip)
        }
      }
    }

    authenticate("user") {
      route("/bonvoyage/trips") {
        get { call.respond(repository.listTrips(call.user().id)) }
        get("/{id}") {
          val id = call.pathUuid("id")
          val trip = repository.getTripAggregate(id, call.user().id)
          call.respond(trip)
        }
      }
    }
  }
}
