package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentWithCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.plugins.pathUuid
import net.barrage.llmao.plugins.query

fun Route.agentsRoutes(agentService: AgentService) {
  route("/agents") {
    get(getAllAgents()) {
      val pagination = call.query(PaginationSort::class)
      val agents = agentService.getAll(pagination, false)
      call.respond(HttpStatusCode.OK, agents)
    }

    get("/{id}", getAgent()) {
      val agentId = call.pathUuid("id")
      val agent = agentService.get(agentId)
      call.respond(HttpStatusCode.OK, agent)
    }
  }
}

// OpenAPI documentation
private fun getAllAgents(): OpenApiRoute.() -> Unit = {
  tags("agents")
  description = "Retrieve list of all agents"
  request {
    queryParameter<Int>("page") {
      description = "Page number for pagination"
      required = false
      example("default") { value = 1 }
    }
    queryParameter<Int>("size") {
      description = "Number of items per page"
      required = false
      example("default") { value = 10 }
    }
    queryParameter<String>("sortBy") {
      description = "Sort by field"
      required = false
      example("default") { value = "name" }
    }
    queryParameter<String>("sortOrder") {
      description = "Sort order (asc or desc)"
      required = false
      example("default") { value = "asc" }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        body<CountedList<Agent>> {
          description = "A list of Agent objects representing all the agents"
        }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agents"
        body<List<AppError>> {}
      }
  }
}

private fun getAgent(): OpenApiRoute.() -> Unit = {
  tags("agents")
  description = "Retrieve agent by ID"
  request {
    pathParameter<KUUID>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        body<AgentWithCollections> { description = "An Agent object representing the agent" }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<AppError>> {}
      }
  }
}
