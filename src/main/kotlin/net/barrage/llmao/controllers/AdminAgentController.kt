package net.barrage.llmao.controllers

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRoute
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.get
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.post
import io.github.smiley4.ktorswaggerui.dsl.routing.resources.put
import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.dtos.PaginationInfo
import net.barrage.llmao.dtos.agents.AgentResponse
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.PaginatedAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.dtos.agents.toPaginatedAgentDTO
import net.barrage.llmao.error.Error
import net.barrage.llmao.models.Agent
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.AgentService

@Resource("admin/agents")
class AdminAgentController(
  val page: Int? = 1,
  val size: Int? = 10,
  val sortBy: String? = "name",
  val sortOrder: String? = "asc",
  val showDeactivated: Boolean? = true,
) {
  @Resource("{id}")
  class Agent(val parent: AdminAgentController, val id: KUUID) {
    @Resource("activate") class Activate(val parent: Agent)

    @Resource("deactivate") class Deactivate(val parent: Agent)
  }
}

fun Route.adminAgentsRoutes(agentService: AgentService) {

  authenticate("auth-session-admin") {
    get<AdminAgentController>(adminGetAllAgents()) {
      val page = it.page ?: 1
      val size = it.size ?: 10
      val sortBy = it.sortBy ?: "name"
      val sortOrder = it.sortOrder ?: "asc"
      val showDeactivated = it.showDeactivated ?: true

      val agents: AgentResponse =
        agentService.getAll(page, size, sortBy, sortOrder, showDeactivated)
      val response =
        toPaginatedAgentDTO(
          agents.agents,
          PaginationInfo(agents.count, page, size, sortBy, sortOrder),
        )
      call.respond(HttpStatusCode.OK, response)
      return@get
    }

    get<AdminAgentController.Agent>(adminGetAgent()) {
      val agent: Agent = agentService.get(it.id)
      call.respond(HttpStatusCode.OK, agent)
      return@get
    }

    post<AdminAgentController>(createAgent()) {
      val newAgent: NewAgentDTO = call.receive()
      val agent: Agent = agentService.create(newAgent)
      call.respond(HttpStatusCode.Created, agent)
      return@post
    }

    put<AdminAgentController.Agent>(updateAgent()) {
      val updatedAgent: UpdateAgentDTO = call.receive()
      val agent: Agent = agentService.update(it.id, updatedAgent)
      call.respond(HttpStatusCode.OK, agent)
      return@put
    }

    put<AdminAgentController.Agent.Activate>(activateAgent()) {
      val agent = agentService.activate(it.parent.id)
      call.respond(HttpStatusCode.OK, agent)
      return@put
    }

    put<AdminAgentController.Agent.Deactivate>(deactivateAgent()) {
      val agent = agentService.deactivate(it.parent.id)
      call.respond(HttpStatusCode.OK, agent)
      return@put
    }
  }
}

// OpenAPI documentation
fun adminGetAllAgents(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
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
    queryParameter<Boolean>("showDeactivated") {
      description = "Show deactivated agents"
      required = false
      example("default") { value = true }
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

fun adminGetAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Retrieve an agent by ID"
  request {
    pathParameter<Int>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
  }
  response {
    HttpStatusCode.OK to { body<Agent> { description = "An Agent object representing the agent" } }
    HttpStatusCode.NotFound to
      {
        description = "Agent not found"
        body<List<Error>> {}
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while retrieving agent"
        body<List<Error>> {}
      }
  }
}

fun createAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Create a new agent"
  request { body<NewAgentDTO> { description = "New agent object" } }
  response {
    HttpStatusCode.Created to
      {
        body<Agent> { description = "An Agent object representing the new agent" }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while creating agent"
        body<List<Error>> {}
      }
  }
}

fun updateAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Update an agent"
  request {
    pathParameter<Int>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
    body<UpdateAgentDTO> { description = "Updated agent object" }
  }
  response {
    HttpStatusCode.OK to
      {
        body<Agent> { description = "An Agent object representing the updated agent" }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while updating agent"
        body<List<Error>> {}
      }
  }
}

fun activateAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Activate an agent"
  request {
    pathParameter<Int>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        body<Agent> { description = "An Agent object representing the activated agent" }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while activating agent"
        body<List<Error>> {}
      }
  }
}

fun deactivateAgent(): OpenApiRoute.() -> Unit = {
  tags("admin/agents")
  description = "Deactivate an agent"
  request {
    pathParameter<Int>("id") {
      description = "Agent ID"
      example("default") { value = 1 }
    }
  }
  response {
    HttpStatusCode.OK to
      {
        body<Agent> { description = "An Agent object representing the deactivated agent" }
      }
    HttpStatusCode.InternalServerError to
      {
        description = "Internal server error occurred while deactivating agent"
        body<List<Error>> {}
      }
  }
}
