package net.barrage.llmao.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.dtos.PaginationInfo
import net.barrage.llmao.dtos.agents.AgentResponse
import net.barrage.llmao.dtos.agents.PaginatedAgentDTO
import net.barrage.llmao.dtos.agents.toPaginatedAgentDTO
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.Agent
import net.barrage.llmao.models.User
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.AgentService

@Resource("agents")
class AgentController(
  val page: Int? = 1,
  val size: Int? = 10,
  val sortBy: String? = "name",
  val sortOrder: String? = "asc",
) {
  @Resource("{id}") class Agent(val parent: AgentController, val id: KUUID)
}

fun Route.agentsRoutes(agentService: AgentService) {
  authenticate("auth-session") {
    get<AgentController>(getAllAgents()) {
      val page = it.page ?: 1
      val size = it.size ?: 10
      val sortBy = it.sortBy ?: "name"
      val sortOrder = it.sortOrder ?: "asc"

      val agents: AgentResponse = agentService.getAll(page, size, sortBy, sortOrder, false)
      val response =
        toPaginatedAgentDTO(
          agents.agents,
          PaginationInfo(agents.count, page, size, sortBy, sortOrder),
        )
      call.respond(HttpStatusCode.OK, response)
      return@get
    }

    get<AgentController.Agent>(getAgent()) {
      val agent: Agent = agentService.get(it.id)
      call.respond(HttpStatusCode.OK, agent)
      return@get
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
        body<PaginatedAgentDTO> {
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
