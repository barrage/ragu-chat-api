package net.barrage.llmao.core.services

import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.CollectionItem
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.UpdateCollectionsResult
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

class AgentService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepository: ChatRepository,
) {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    return agentRepository.getAll(pagination, showDeactivated)
  }

  fun getAllAdmin(
    pagination: PaginationSort,
    showDeactivated: Boolean,
  ): CountedList<AgentWithConfiguration> {
    return agentRepository.getAllAdmin(pagination, showDeactivated)
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
    return agentRepository.get(id)
  }

  suspend fun create(create: CreateAgent): AgentWithConfiguration {
    validateAgentConfigurationParams(
      llmProvider = create.configuration.llmProvider,
      model = create.configuration.model,
      vectorProvider = create.vectorProvider,
      embeddingProvider = create.embeddingProvider,
      embeddingModel = create.embeddingModel,
    )

    return agentRepository.create(create) ?: throw IllegalStateException("Something went wrong")
  }

  suspend fun update(id: KUUID, update: UpdateAgent): Any {
    validateAgentConfigurationParams(
      llmProvider = update.configuration?.llmProvider,
      model = update.configuration?.model,
      embeddingProvider = update.embeddingProvider,
      embeddingModel = update.embeddingModel,
    )

    // If embedding configuration is being updated, invalidate all collections.
    if (update.embeddingProvider != null && update.embeddingModel != null) {
      agentRepository.deleteAllCollections(id)
    }

    return agentRepository.update(id, update)
  }

  suspend fun updateCollections(
    agentId: KUUID,
    update: UpdateCollections,
  ): UpdateCollectionsResult {
    val vectorDb = providers.vector.getProvider(update.provider)

    val agent = agentRepository.get(agentId)
    val vectorSize =
      providers.embedding
        .getProvider(agent.agent.embeddingProvider)
        .vectorSize(agent.agent.embeddingModel)

    val verifiedCollections = mutableListOf<CollectionItem>()
    val failedCollections = mutableListOf<CollectionItem>()

    // Ensure the collections being added exist
    val filteredUpdate: UpdateCollections =
      if (update.add != null) {
        for (collectionItem in update.add) {
          if (vectorDb.validateCollection(collectionItem.name, vectorSize)) {
            verifiedCollections.add(collectionItem)
          } else {
            failedCollections.add(collectionItem)
          }
        }

        UpdateCollections(
          provider = update.provider,
          add = verifiedCollections,
          remove = update.remove,
        )
      } else {
        update
      }

    agentRepository.updateCollections(agentId, update)
    return UpdateCollectionsResult(verifiedCollections, update.remove.orEmpty(), failedCollections)
  }

  /**
   * Checks whether the providers and their respective models are supported. Data passed to this
   * function should come from already validated DTOs.
   */
  private suspend fun validateAgentConfigurationParams(
    llmProvider: String? = null,
    model: String? = null,
    vectorProvider: String? = null,
    embeddingProvider: String? = null,
    embeddingModel: String? = null,
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

  fun getAgentConfigurationVersions(
    agentId: KUUID,
    pagination: PaginationSort,
  ): CountedList<AgentConfiguration> {
    return agentRepository.getAgentConfigurationVersions(agentId, pagination)
  }

  fun getAgentConfigurationWithEvaluationCounts(
    agentId: KUUID,
    versionId: KUUID,
  ): AgentConfigurationWithEvaluationCounts {
    val agentConfiguration = agentRepository.getAgentConfiguration(agentId, versionId)
    val configurationMessageCounts = chatRepository.getAgentConfigurationMessageCounts(versionId)
    return AgentConfigurationWithEvaluationCounts(agentConfiguration, configurationMessageCounts)
  }

  fun getAgentConfigurationEvaluatedMessages(
    agentId: KUUID,
    versionId: KUUID,
    evaluation: Boolean? = null,
    pagination: PaginationSort,
  ): CountedList<Message> {
    agentRepository.getAgentConfiguration(agentId, versionId)

    return chatRepository.getAgentConfigurationEvaluatedMessages(versionId, evaluation, pagination)
  }

  fun rollbackVersion(agentId: KUUID, versionId: KUUID): AgentWithConfiguration {
    agentRepository.getAgentConfiguration(agentId, versionId)

    return agentRepository.rollbackVersion(agentId, versionId)
  }

  fun getAgent(agentId: KUUID): Agent {
    return agentRepository.getAgent(agentId)
  }
}
