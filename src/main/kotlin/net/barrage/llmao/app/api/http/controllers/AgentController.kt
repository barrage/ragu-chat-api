package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktoropenapi.config.RouteConfig
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.app.api.http.pathUuid
import net.barrage.llmao.app.api.http.query
import net.barrage.llmao.app.api.http.queryPaginationSort
import net.barrage.llmao.app.api.http.user
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.api.AgentService
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.types.KUUID

fun Route.agentsRoutes(agentService: AgentService) {
  route("/agents") {
    get(getAllAgents()) {
      val pagination = call.query(PaginationSort::class)
      val user = call.user()
      val agents =
        agentService.userListAgents(pagination, showDeactivated = false, groups = user.entitlements)
      call.respond(HttpStatusCode.OK, agents)
    }

    get("/{id}", getAgent()) {
      val agentId = call.pathUuid("id")
      val user = call.user()
      val agent = agentService.userGetAgent(agentId, user.entitlements)
      call.respond(HttpStatusCode.OK, agent)
    }
  }
}

// OpenAPI documentation
private fun getAllAgents(): RouteConfig.() -> Unit = {
  tags("agents")
  description = "Retrieve list of all agents"
  request { queryPaginationSort() }
  response {
    HttpStatusCode.OK to
      {
        description = "A list of Agent objects representing all the agents"
        body<CountedList<Agent>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agents"
        body<List<AppError>> {}
      }
  }
}

private fun getAgent(): RouteConfig.() -> Unit = {
  tags("agents")
  description = "Retrieve agent by ID"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("example") { value = "a923b56f-528d-4a31-ac2f-78810069488e" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        description = "An Agent object representing the agent"
        body<Agent> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<AppError>> {}
      }
  }
}
