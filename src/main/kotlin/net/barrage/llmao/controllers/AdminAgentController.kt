package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.resources.*
import io.ktor.server.resources.post
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.models.Agent
import net.barrage.llmao.services.AgentService

@Resource("admin/agents")
class AdminAgentController {
  @Resource("{id}")
  class Agent(val parent: AdminAgentController, val id: Int) {
    @Resource("activate") class Activate(val parent: Agent)

    @Resource("deactivate") class Deactivate(val parent: Agent)
  }
}

fun Route.adminAgentsRoutes() {
  val agentService = AgentService()

  authenticate("auth-session-admin") {
    get<AdminAgentController> {
      val agents: List<Agent> = agentService.getAll()
      call.respond(HttpStatusCode.OK, agents)
      return@get
    }

    get<AdminAgentController.Agent> {
      val agent: Agent = agentService.get(it.id)
      call.respond(HttpStatusCode.OK, agent)
      return@get
    }

    post<AdminAgentController> {
      val newAgent: NewAgentDTO = call.receive()
      val agent: Agent = agentService.create(newAgent)
      call.respond(HttpStatusCode.Created, agent)
      return@post
    }

    put<AdminAgentController.Agent> {
      val updatedAgent: UpdateAgentDTO = call.receive()
      val agent: Agent = agentService.update(it.id, updatedAgent)
      call.respond(HttpStatusCode.OK, agent)
      return@put
    }

    put<AdminAgentController.Agent.Activate> {
      val agent = agentService.activate(it.parent.id)
      call.respond(HttpStatusCode.OK, agent)
      return@put
    }

    put<AdminAgentController.Agent.Deactivate> {
      val agent = agentService.deactivate(it.parent.id)
      call.respond(HttpStatusCode.OK, agent)
      return@put
    }
  }
}
