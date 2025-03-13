package net.barrage.llmao.core.agent

import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolRegistry
import net.barrage.llmao.core.model.Agent
import net.barrage.llmao.core.model.AgentConfiguration
import net.barrage.llmao.core.model.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.model.AgentFull
import net.barrage.llmao.core.model.AgentTool
import net.barrage.llmao.core.model.AgentUpdateTools
import net.barrage.llmao.core.model.AgentWithConfiguration
import net.barrage.llmao.core.model.CollectionInsert
import net.barrage.llmao.core.model.CreateAgent
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.UpdateAgent
import net.barrage.llmao.core.model.UpdateCollections
import net.barrage.llmao.core.model.UpdateCollectionsFailure
import net.barrage.llmao.core.model.UpdateCollectionsResult
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.model.common.SearchFiltersAdminAgents
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.types.KUUID

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.agent")

class AgentService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepositoryRead: ChatRepositoryRead,
  private val stateChangeListener: EventListener<StateChangeEvent>,
  private val avatarStorage: ImageStorage,
) {
  suspend fun listAgents(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    return agentRepository.getAll(pagination, showDeactivated)
  }

  suspend fun listAgentsAdmin(
    pagination: PaginationSort,
    filters: SearchFiltersAdminAgents,
  ): CountedList<AgentWithConfiguration> {
    return agentRepository.getAllAdmin(pagination, filters)
  }

  suspend fun getActive(id: KUUID): Agent {
    return agentRepository.getActive(id)
  }

  /**
   * Get an agent with full configuration, with its instructions populated with placeholders for
   * display purposes.
   */
  suspend fun getFull(id: KUUID): AgentFull {
    return agentRepository.get(id)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")
  }

  suspend fun create(create: CreateAgent): AgentWithConfiguration {
    providers.validateSupportedConfigurationParams(
      llmProvider = create.configuration.llmProvider,
      model = create.configuration.model,
    )

    return agentRepository.create(create)
  }

  suspend fun update(id: KUUID, update: UpdateAgent): Any {
    providers.validateSupportedConfigurationParams(
      llmProvider = update.configuration?.llmProvider,
      model = update.configuration?.model,
    )

    if (update.active == false) {
      stateChangeListener.dispatch(StateChangeEvent.AgentDeactivated(id))
    }

    return agentRepository.update(id, update)
  }

  suspend fun delete(id: KUUID) {
    val agent = agentRepository.getAgent(id)

    if (agent.active) {
      throw AppError.api(ErrorReason.InvalidOperation, "Cannot delete active agent")
    }

    if (agent.avatar != null) {
      avatarStorage.delete(agent.avatar)
    }

    agentRepository.delete(id)
  }

  suspend fun updateCollections(
    agentId: KUUID,
    update: UpdateCollections,
  ): UpdateCollectionsResult {
    val (additions, failures) = processAdditions(providers, update)

    agentRepository.updateCollections(agentId, additions, update.remove)

    return UpdateCollectionsResult(additions.map { it.info }, update.remove.orEmpty(), failures)
  }

  suspend fun removeCollectionFromAllAgents(collectionName: String, provider: String) {
    agentRepository.removeCollectionFromAll(collectionName, provider)
  }

  suspend fun getAgentConfigurationVersions(
    agentId: KUUID,
    pagination: PaginationSort,
  ): CountedList<AgentConfiguration> {
    return agentRepository.getAgentConfigurationVersions(agentId, pagination)
  }

  suspend fun getAgentConfigurationWithEvaluationCounts(
    agentId: KUUID,
    versionId: KUUID,
  ): AgentConfigurationWithEvaluationCounts {
    val agentConfiguration = agentRepository.getAgentConfiguration(agentId, versionId)
    val configurationMessageCounts =
      chatRepositoryRead.getAgentConfigurationMessageCounts(versionId)
    return AgentConfigurationWithEvaluationCounts(agentConfiguration, configurationMessageCounts)
  }

  suspend fun getAgentConfigurationEvaluatedMessages(
    agentId: KUUID,
    versionId: KUUID,
    evaluation: Boolean? = null,
    pagination: PaginationSort,
  ): CountedList<MessageGroupAggregate> {
    agentRepository.getAgentConfiguration(agentId, versionId)

    return chatRepositoryRead.getAgentConfigurationEvaluatedMessages(
      versionId,
      evaluation,
      pagination,
    )
  }

  suspend fun rollbackVersion(agentId: KUUID, versionId: KUUID): AgentWithConfiguration {
    agentRepository.getAgentConfiguration(agentId, versionId)

    return agentRepository.rollbackVersion(agentId, versionId)
  }

  suspend fun getAgent(agentId: KUUID): Agent {
    return agentRepository.getAgent(agentId)
  }

  suspend fun uploadAgentAvatar(id: KUUID, image: Image) {
    val agent = agentRepository.getAgent(id)

    if (agent.avatar != null) {
      avatarStorage.delete(agent.avatar)
    }

    avatarStorage.store(image)

    agentRepository.updateAvatar(id, image.name)
  }

  suspend fun deleteAgentAvatar(id: KUUID) {
    val agent = agentRepository.getAgent(id)

    if (agent.avatar == null) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent avatar not found")
    }

    avatarStorage.delete(agent.avatar)

    agentRepository.removeAvatar(id)
  }

  fun listAvailableAgentTools(): List<ToolDefinition> {
    return ToolRegistry.listToolDefinitions()
  }

  suspend fun listAgentTools(agentId: KUUID): List<AgentTool> {
    return agentRepository.getAgentTools(agentId)
  }

  suspend fun updateAgentTools(agentId: KUUID, update: AgentUpdateTools) {
    for (toolName in update.add) {
      if (ToolRegistry.getToolDefinition(toolName) == null) {
        throw AppError.api(ErrorReason.EntityDoesNotExist, "Tool '$toolName' does not exist")
      }
    }
    agentRepository.updateAgentTools(agentId, update)
  }
}

fun processAdditions(
  providers: ProviderState,
  update: UpdateCollections,
): Pair<List<CollectionInsert>, List<UpdateCollectionsFailure>> {
  val additions = mutableListOf<CollectionInsert>()
  val failures = mutableListOf<UpdateCollectionsFailure>()

  update.add?.forEach { collectionAdd ->
    val vectorDb = providers.vector.getProvider(collectionAdd.provider)

    // Ensure the collections being added exist
    val collection = vectorDb.getCollectionInfo(collectionAdd.name)

    if (collection != null) {
      additions.add(CollectionInsert(collectionAdd.amount, collectionAdd.instruction, collection))
    } else {
      LOG.warn("Collection '${collectionAdd.name}' does not exist")
      failures.add(UpdateCollectionsFailure(collectionAdd.name, "Collection does not exist"))
    }
  }

  return Pair(additions, failures)
}
