package net.barrage.llmao.services

import io.ktor.server.plugins.*
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.dtos.agents.AgentResponse
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.models.Agent
import net.barrage.llmao.serializers.KUUID

class AgentService(private val agentRepository: AgentRepository) {
  fun getAll(
    page: Int,
    size: Int,
    sortBy: String,
    sortOrder: String,
    showDeactivated: Boolean,
  ): AgentResponse {
    val offset = (page - 1) * size

    val agents = agentRepository.getAll(offset, size, sortBy, sortOrder, showDeactivated)
    val count = agentRepository.countAll(showDeactivated)

    return AgentResponse(agents, count)
  }

  fun get(id: KUUID): Agent {
    return agentRepository.get(id) ?: throw NotFoundException("Agent not found")
  }

  fun create(newAgent: NewAgentDTO): Agent {
    return agentRepository.create(newAgent) ?: throw IllegalStateException("Something went wrong")
  }

  fun update(id: KUUID, updated: UpdateAgentDTO): Agent {
    return agentRepository.update(id, updated) ?: throw NotFoundException("Agent not found")
  }

  fun activate(id: KUUID): Agent {
    return agentRepository.activate(id) ?: throw NotFoundException("Agent not found")
  }

  fun deactivate(id: KUUID): Agent {
    return agentRepository.deactivate(id) ?: throw NotFoundException("Agent not found")
  }
}
