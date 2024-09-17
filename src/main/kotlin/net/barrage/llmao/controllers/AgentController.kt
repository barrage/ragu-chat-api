package net.barrage.llmao.controllers

import io.ktor.http.*
import io.ktor.resources.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.resources.*
import io.ktor.server.resources.put
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.sessions.*
import net.barrage.llmao.models.Agent
import net.barrage.llmao.models.UserSession
import net.barrage.llmao.services.AgentService
import net.barrage.llmao.services.SessionService

@Resource("agents")
class AgentController {
    @Resource("{id}")
    class Agent(val parent: AgentController, val id: Int)
}

fun Route.agentsRoutes() {
    val agentService = AgentService()

    authenticate("auth-session") {
        get<AgentController> {
            val agents: List<Agent> = agentService.getAll()
            call.respond(HttpStatusCode.OK, agents)
            return@get
        }

        get<AgentController.Agent> {
            val agent: Agent = agentService.get(it.id)
            call.respond(HttpStatusCode.OK, agent)
            return@get
        }

        put<AgentController.Agent> {
            val agentId: Int = it.id
            val userSession = call.sessions.get<UserSession>()
            val userId = SessionService().get(userSession!!.id)?.userId!!
            val user = agentService.setDefault(agentId, userId)
            call.respond(HttpStatusCode.OK, user)
            return@put
        }
    }
}