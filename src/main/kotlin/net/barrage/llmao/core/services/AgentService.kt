package net.barrage.llmao.core.services

import io.ktor.server.plugins.*
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.types.KUUID

class AgentService(private val agentRepository: AgentRepository) {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    return agentRepository.getAll(pagination, showDeactivated)
  }

  fun get(id: KUUID): Agent {
    return agentRepository.get(id) ?: throw NotFoundException("Agent not found")
  }

  fun create(newAgent: CreateAgent): Agent {
    return agentRepository.create(newAgent) ?: throw IllegalStateException("Something went wrong")
  }

  fun update(id: KUUID, updated: UpdateAgent): Agent {
    return agentRepository.update(id, updated) ?: throw NotFoundException("Agent not found")
  }

  fun activate(id: KUUID): Agent {
    return agentRepository.activate(id) ?: throw NotFoundException("Agent not found")
  }

  fun deactivate(id: KUUID): Agent {
    return agentRepository.deactivate(id) ?: throw NotFoundException("Agent not found")
  }
}