package net.barrage.llmao.app.workflow.chat.api

import net.barrage.llmao.app.workflow.chat.ChatToolExecutor
import net.barrage.llmao.app.workflow.chat.LOG
import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.app.workflow.chat.model.AgentDeactivated
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.model.AgentGroupUpdate
import net.barrage.llmao.app.workflow.chat.model.AgentTool
import net.barrage.llmao.app.workflow.chat.model.AgentUpdateTools
import net.barrage.llmao.app.workflow.chat.model.AgentWithConfiguration
import net.barrage.llmao.app.workflow.chat.model.CollectionInsert
import net.barrage.llmao.app.workflow.chat.model.CreateAgent
import net.barrage.llmao.app.workflow.chat.model.SearchFiltersAdminAgents
import net.barrage.llmao.app.workflow.chat.model.UpdateAgent
import net.barrage.llmao.app.workflow.chat.model.UpdateCollections
import net.barrage.llmao.app.workflow.chat.model.UpdateCollectionsFailure
import net.barrage.llmao.app.workflow.chat.model.UpdateCollectionsResult
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.app.workflow.chat.repository.ChatRepositoryRead
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.blob.AVATARS_PATH
import net.barrage.llmao.core.blob.BlobStorage
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.model.Image
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.types.KUUID

class AdminAgentService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepositoryRead: ChatRepositoryRead,

  /** The image storage provider for avatars. */
  private val avatarStorage: BlobStorage<Image>,
  private val listener: EventListener,
) {

  suspend fun listAgents(
    pagination: PaginationSort,
    filters: SearchFiltersAdminAgents,
  ): CountedList<AgentWithConfiguration> = agentRepository.getAllAdmin(pagination, filters)

  /** Get an agent with full configuration. */
  suspend fun getFull(id: KUUID): AgentFull =
    agentRepository.get(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

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
      listener.dispatch(AgentDeactivated(id))
    }

    return agentRepository.update(id, update)
  }

  suspend fun delete(id: KUUID) {
    val agent = agentRepository.getAgent(id) ?: throw AppError.api(ErrorReason.EntityDoesNotExist)

    if (agent.avatar != null) {
      avatarStorage.delete(agent.avatar)
    }

    agentRepository.delete(id)
  }

  suspend fun updateCollections(
    agentId: KUUID,
    update: UpdateCollections,
  ): UpdateCollectionsResult {
    val additions = mutableListOf<CollectionInsert>()
    val failures = mutableListOf<UpdateCollectionsFailure>()

    update.add?.forEach { collectionAdd ->
      val vectorDb = providers.vector[collectionAdd.provider]

      // Ensure the collections being added exist
      val collection = vectorDb.getCollectionInfo(collectionAdd.name)

      if (collection != null) {
        additions.add(
          CollectionInsert(
            amount = collectionAdd.amount,
            instruction = collectionAdd.instruction,
            maxDistance = collectionAdd.maxDistance,
            info = collection,
          )
        )
      } else {
        LOG.warn("Collection '${collectionAdd.name}' does not exist")
        failures.add(UpdateCollectionsFailure(collectionAdd.name, "Collection does not exist"))
      }
    }

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
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")
  }

  /**
   * Store the agent avatar in the image storage and repository.
   *
   * Returns the path to the image, whose semantics are defined by the underlying storage.
   */
  suspend fun uploadAgentAvatar(agentId: KUUID, image: Image): String {
    val agent =
      agentRepository.getAgent(agentId) ?: throw AppError.api(ErrorReason.EntityDoesNotExist)

    if (agent.avatar != null) {
      avatarStorage.delete(agent.avatar)
    }

    val path = "${agent.id}.${image.type}"

    avatarStorage.store("$AVATARS_PATH/$path", image)

    agentRepository.updateAvatar(agentId, path)

    return path
  }

  suspend fun deleteAgentAvatar(id: KUUID) {
    val agent =
      agentRepository.getAgent(id)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

    if (agent.avatar == null) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent avatar not found")
    }

    agentRepository.removeAvatar(id)

    avatarStorage.delete("$AVATARS_PATH/${agent.avatar}")
  }

  fun listAvailableAgentTools(): List<ToolDefinition> {
    return ChatToolExecutor.listToolDefinitions()
  }

  suspend fun listAgentTools(agentId: KUUID): List<AgentTool> {
    return agentRepository.getAgentTools(agentId)
  }

  suspend fun updateAgentTools(agentId: KUUID, update: AgentUpdateTools) {
    for (toolName in update.add) {
      if (ChatToolExecutor.getToolDefinition(toolName) == null) {
        throw AppError.api(ErrorReason.EntityDoesNotExist, "Tool '$toolName' does not exist")
      }
    }
    agentRepository.updateAgentTools(agentId, update)
  }

  suspend fun updateGroups(agentId: KUUID, update: AgentGroupUpdate) {
    agentRepository.updateGroups(agentId, update)
  }

  suspend fun exportAll(): List<AgentFull> {
    return agentRepository.listAllFull()
  }

  suspend fun import(agents: List<AgentFull>) {
    agentRepository.import(agents)
  }
}
