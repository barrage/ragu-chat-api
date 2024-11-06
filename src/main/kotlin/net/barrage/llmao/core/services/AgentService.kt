package net.barrage.llmao.core.services

import io.ktor.server.plugins.*
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentInstructions
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AgentService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
) {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    return agentRepository.getAll(pagination, showDeactivated)
  }

  fun get(id: KUUID): AgentFull {
    return agentRepository.get(id)
  }

  fun getActive(id: KUUID): Agent {
    return agentRepository.getActive(id)
  }

  /**
   * Get an agent with full configuration, with its instructions populated with placeholders for
   * display purposes.
   */
  fun getDisplay(id: KUUID): AgentFull {
    val agent = agentRepository.get(id)
    val instructions =
      AgentInstructions(
        agent.instructions.title("<PROMPT>", agent.agent.language),
        agent.instructions.language(agent.agent.language),
        agent.instructions.summary("<HISTORY>", agent.agent.language),
      )
    return AgentFull(agent.agent, instructions, agent.collections)
  }

  suspend fun create(create: CreateAgent): Agent {
    validateAgentParams(
      create.llmProvider,
      create.model,
      create.vectorProvider,
      create.embeddingProvider,
      create.embeddingModel,
    )
    return agentRepository.create(create) ?: throw IllegalStateException("Something went wrong")
  }

  suspend fun update(id: KUUID, update: UpdateAgent): Agent {
    validateAgentParams(
      update.llmProvider,
      update.model,
      update.vectorProvider,
      update.embeddingProvider,
      update.embeddingModel,
    )
    return agentRepository.update(id, update) ?: throw NotFoundException("Agent not found")
  }

  fun updateCollections(agentId: KUUID, update: UpdateCollections) {
    val vectorDb = providers.vector.getProvider(update.provider)

    // Ensure the collections being added exist
    update.add?.let {
      for (collectionItem in it) {
        if (!vectorDb.validateCollection(collectionItem.name)) {
          throw AppError.api(
            ErrorReason.EntityDoesNotExist,
            "Collection with name '${collectionItem.name}'",
          )
        }
      }
    }

    return agentRepository.updateCollections(agentId, update)
  }

  /**
   * Checks whether the providers and their respective models are supported. Data passed to this
   * function should come from already validated DTOs.
   */
  private suspend fun validateAgentParams(
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
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Provider '${llm.id()}' does not support model '${model}'",
        )
      }
    }

    if (vectorProvider != null) {
      // Throws if invalid provider
      providers.vector.getProvider(vectorProvider)
    }

    if (embeddingProvider != null && embeddingModel != null) {
      val embedder = providers.embedding.getProvider(embeddingProvider)
      if (!embedder.supportsModel(embeddingModel)) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Provider '${embedder.id()}' does not support model '${embeddingModel}'",
        )
      }
    }
  }
}
