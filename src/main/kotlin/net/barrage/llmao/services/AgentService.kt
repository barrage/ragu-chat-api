package net.barrage.llmao.services

import io.ktor.server.plugins.*
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.models.Agent
import net.barrage.llmao.repositories.AgentRepository

class AgentService {
    private val agentRepository = AgentRepository()

    fun getAll(): List<Agent> {
        return agentRepository.getAll()
    }

    fun get(id: Int): Agent {
        return agentRepository.get(id) ?: throw NotFoundException("Agent not found")
    }

    fun create(newAgent: NewAgentDTO): Agent {
        return agentRepository.create(newAgent) ?: throw IllegalStateException("Something went wrong")
    }

    fun update(id: Int, updated: UpdateAgentDTO): Agent {
        return agentRepository.update(id, updated) ?: throw NotFoundException("Agent not found")
    }

    fun delete(id: Int): Unit {
        if (agentRepository.delete(id)) {
            throw NotFoundException("Agent not found")
        }
    }

    fun activate(id: Int): Agent {
        return agentRepository.activate(id) ?: throw NotFoundException("Agent not found")
    }

    fun deactivate(id: Int): Agent {
        return agentRepository.deactivate(id) ?: throw NotFoundException("Agent not found")
    }
}