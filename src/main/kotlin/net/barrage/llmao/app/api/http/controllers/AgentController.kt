package net.barrage.llmao.app.api.http.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.services.AgentService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.Error

@Resource("agents")
class AgentController(val pagination: PaginationSort) {
  @Resource("{id}") class Agent(val parent: AgentController, val id: KUUID)
}

fun Route.agentsRoutes(agentService: AgentService) {
  authenticate("auth-session") {
    get<AgentController>(getAllAgents()) {
      val agents = agentService.getAll(it.pagination, false)
      call.respond(HttpStatusCode.OK, agents)
    }

    get<AgentController.Agent>(getAgent()) {
      val agent: Agent = agentService.get(it.id)
      call.respond(HttpStatusCode.OK, agent)
    }
  }
}

// OpenAPI documentation
fun getAllAgents(): OpenApiRoute.() -> Unit = {
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
        body<List<Error>> {}
      }
  }
}

fun getAgent(): OpenApiRoute.() -> Unit = {
  tags("agents")
  description = "Retrieve agent by ID"
  request {
    pathParameter<Int>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
  }
  response {
    HttpStatusCode.OK to { body<Agent> { description = "An Agent object representing the agent" } }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<Error>> {}
      }
  }
}

fun defaultAgent(): OpenApiRoute.() -> Unit = {
  tags("agents")
  description = "Set default agent"
  request {
    pathParameter<Int>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
  }
  response {
    HttpStatusCode.OK to { body<User> { description = "An User object representing the user" } }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while setting default agent"
        body<List<Error>> {}
      }
  }
}
