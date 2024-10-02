package net.barrage.llmao.core.services

import io.ktor.server.plugins.*
import net.barrage.llmao.ProviderState
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.apiError

class AgentService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
) {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    return agentRepository.getAll(pagination, showDeactivated)
  }

  fun get(id: KUUID): Agent {
    return agentRepository.get(id) ?: throw NotFoundException("Agent not found")
  }

  fun create(newAgent: CreateAgent): Agent {
    return agentRepository.create(newAgent) ?: throw IllegalStateException("Something went wrong")
  }

  fun update(id: KUUID, update: UpdateAgent): Agent {
    validateAgentParams(
      update.llmProvider,
      update.model,
      update.vectorProvider,
      update.embeddingProvider,
      update.embeddingModel,
    )
    return agentRepository.update(id, update) ?: throw NotFoundException("Agent not found")
  }

  /**
   * Checks whether the providers and their respective models are supported. Data passed to this
   * function should come from already validated DTOs.
   */
  private fun validateAgentParams(
    llmProvider: String?,
    model: String?,
    vectorProvider: String?,
    embeddingProvider: String?,
    embeddingModel: String?,
  ) {
    if (llmProvider != null && model != null) {
      // Throws if invalid provider
      val llm = providers.llm.getProvider(llmProvider)
      if (!llm.supportsModel(model)) {
        throw apiError("Invalid Model", "Provider '${llm.id()}' does not support model '${model}'")
      }
    }

    if (vectorProvider != null) {
      // Throws if invalid provider
      providers.vector.getProvider(vectorProvider)
    }

    if (embeddingProvider != null && embeddingModel != null) {
      val embedder = providers.embedding.getProvider(embeddingProvider)
      if (!embedder.supportsModel(embeddingModel)) {
        throw apiError(
          "Invalid Model",
          "Provider '${embedder.id()}' does not support model '${embeddingModel}'",
        )
      }
    }
  }
}
