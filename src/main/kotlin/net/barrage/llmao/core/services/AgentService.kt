package net.barrage.llmao.core.services

import io.ktor.util.*
import io.ktor.utils.io.*
import net.barrage.llmao.app.ProviderState
import net.barrage.llmao.core.EventListener
import net.barrage.llmao.core.StateChangeEvent
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentConfigurationWithEvaluationCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.CollectionInsert
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.UpdateCollectionsFailure
import net.barrage.llmao.core.models.UpdateCollectionsResult
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.repository.ChatRepository
import net.barrage.llmao.core.storage.ImageStorage
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.services.AgentService")

class AgentService(
  private val providers: ProviderState,
  private val agentRepository: AgentRepository,
  private val chatRepository: ChatRepository,
  private val stateChangeListener: EventListener<StateChangeEvent>,
  private val avatarStorage: ImageStorage,
) {
  suspend fun getAll(
    pagination: PaginationSort,
    showDeactivated: Boolean,
    withAvatar: Boolean = false,
  ): CountedList<Agent> {
    return agentRepository.getAll(pagination, showDeactivated).apply {
      if (withAvatar) {
        items.forEach { it.avatar = avatarStorage.retrieve(it.id) }
      }
    }
  }

  suspend fun getAllAdmin(
    pagination: PaginationSort,
    name: String?,
    active: Boolean?,
    withAvatar: Boolean = false,
  ): CountedList<AgentWithConfiguration> {
    return agentRepository.getAllAdmin(pagination, name, active).apply {
      if (withAvatar) {
        items.forEach { it.agent.avatar = avatarStorage.retrieve(it.agent.id) }
      }
    }
  }

  suspend fun get(id: KUUID, withAvatar: Boolean = false): AgentFull {
    return agentRepository.get(id).apply {
      if (withAvatar) {
        agent.avatar = avatarStorage.retrieve(id)
      }
    }
  }

  suspend fun getActive(id: KUUID): Agent {
    return agentRepository.getActive(id)
  }

  /**
   * Get an agent with full configuration, with its instructions populated with placeholders for
   * display purposes.
   */
  suspend fun getFull(id: KUUID, withAvatar: Boolean = false): AgentFull {
    return agentRepository.get(id).apply {
      if (withAvatar) {
        agent.avatar = avatarStorage.retrieve(id)
      }
    }
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
    val count = agentRepository.delete(id)

    if (count == 0) {
      throw AppError.api(
        ErrorReason.InvalidParameter,
        "Cannot delete active agent or agent not found",
      )
    }

    avatarStorage.delete(id)
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
    val configurationMessageCounts = chatRepository.getAgentConfigurationMessageCounts(versionId)
    return AgentConfigurationWithEvaluationCounts(agentConfiguration, configurationMessageCounts)
  }

  suspend fun getAgentConfigurationEvaluatedMessages(
    agentId: KUUID,
    versionId: KUUID,
    evaluation: Boolean? = null,
    pagination: PaginationSort,
  ): CountedList<Message> {
    agentRepository.getAgentConfiguration(agentId, versionId)

    return chatRepository.getAgentConfigurationEvaluatedMessages(versionId, evaluation, pagination)
  }

  suspend fun rollbackVersion(agentId: KUUID, versionId: KUUID): AgentWithConfiguration {
    agentRepository.getAgentConfiguration(agentId, versionId)

    return agentRepository.rollbackVersion(agentId, versionId)
  }

  suspend fun getAgent(agentId: KUUID, withAvatar: Boolean = false): Agent {
    return agentRepository.getAgent(agentId).apply {
      if (withAvatar) {
        avatar = avatarStorage.retrieve(id)
      }
    }
  }

  suspend fun uploadAgentAvatar(id: KUUID, fileExtension: String, avatar: ByteReadChannel): Agent {
    val agent = agentRepository.getAgent(id)

    agent.avatar = avatarStorage.store(id, fileExtension, avatar.toByteArray())

    return agent
  }

  suspend fun deleteAgentAvatar(id: KUUID) {
    agentRepository.getAgent(id)

    avatarStorage.delete(id)
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
