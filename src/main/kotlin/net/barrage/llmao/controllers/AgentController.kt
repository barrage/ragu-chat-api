package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.dtos.PaginationInfo
import net.barrage.llmao.dtos.agents.AgentResponse
import net.barrage.llmao.dtos.agents.toPaginatedAgentDTO
import net.barrage.llmao.models.Agent
import net.barrage.llmao.models.UserContext
import net.barrage.llmao.services.AgentService

@Resource("agents")
class AgentController(
  val page: Int? = 1,
  val size: Int? = 10,
  val sortBy: String? = "name",
  val sortOrder: String? = "asc",
) {
  @Resource("{id}") class Agent(val parent: AgentController, val id: Int)
}

fun Route.agentsRoutes() {
  val agentService = AgentService()

  authenticate("auth-session") {
    get<AgentController> {
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

    get<AgentController.Agent> {
      val agent: Agent = agentService.get(it.id)
      call.respond(HttpStatusCode.OK, agent)
      return@get
    }

    put<AgentController.Agent> {
      val agentId: Int = it.id
      val currentUser = UserContext.currentUser
      val user = agentService.setDefault(agentId, currentUser!!.id)
      call.respond(HttpStatusCode.OK, user)
      return@put
    }
  }
}
