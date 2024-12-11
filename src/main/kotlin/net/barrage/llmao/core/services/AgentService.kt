package net.barrage.llmao.core.services

import net.barrage.llmao.app.ProviderState
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
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

private val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.services.AgentService")

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
    providers.validateSupportedConfigurationParams(
      llmProvider = create.configuration.llmProvider,
      model = create.configuration.model,
    )

    return agentRepository.create(create) ?: throw IllegalStateException("Something went wrong")
  }

  suspend fun update(id: KUUID, update: UpdateAgent): Any {
    providers.validateSupportedConfigurationParams(
      llmProvider = update.configuration?.llmProvider,
      model = update.configuration?.model,
    )

    return agentRepository.update(id, update)
  }

  fun delete(id: KUUID) {
    val count = agentRepository.delete(id)
    if (count == 0) {
      throw AppError.api(
        ErrorReason.InvalidParameter,
        "Cannot delete active agent or agent not found",
      )
    }
  }

  fun updateCollections(agentId: KUUID, update: UpdateCollections): UpdateCollectionsResult {
    val (additions, failures) = processAdditions(providers, update)

    agentRepository.updateCollections(agentId, additions, update.remove)

    return UpdateCollectionsResult(additions.map { it.info }, update.remove.orEmpty(), failures)
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
