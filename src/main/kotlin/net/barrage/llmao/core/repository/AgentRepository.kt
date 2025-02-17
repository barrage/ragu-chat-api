package net.barrage.llmao.core.repository

import io.ktor.util.logging.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentTool
import net.barrage.llmao.core.models.AgentToolCall
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.CollectionInsert
import net.barrage.llmao.core.models.CollectionRemove
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateAgentConfiguration
import net.barrage.llmao.core.models.UpdateTools
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SearchFiltersAdminAgents
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toAgentCollection
import net.barrage.llmao.core.models.toAgentConfiguration
import net.barrage.llmao.core.models.toAgentTool
import net.barrage.llmao.core.models.toAgentToolCall
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.AGENT_TOOLS
import net.barrage.llmao.tables.references.AGENT_TOOL_CALLS
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.excluded
import org.jooq.kotlin.coroutines.transactionCoroutine

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.repository.AgentRepository")

class AgentRepository(private val dslContext: DSLContext) {
  suspend fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    val order = getSortOrderAgent(pagination, admin = false)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .awaitSingle()
        .value1() ?: 0

    val agents =
      dslContext
        .select(
          AGENTS.ID,
          AGENTS.NAME,
          AGENTS.DESCRIPTION,
          AGENTS.ACTIVE,
          AGENTS.ACTIVE_CONFIGURATION_ID,
          AGENTS.LANGUAGE,
          AGENTS.AVATAR,
          AGENTS.CREATED_AT,
          AGENTS.UPDATED_AT,
        )
        .from(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { it.into(AGENTS).toAgent() }
        .toList()

    return CountedList(total, agents)
  }

  suspend fun getAllAdmin(
    pagination: PaginationSort,
    filters: SearchFiltersAdminAgents,
  ): CountedList<AgentWithConfiguration> {
    val order = getSortOrderAgent(pagination, admin = true)
    val (limit, offset) = pagination.limitOffset()

    val conditions = filters.generateConditions()
    val total = dslContext.selectCount().from(AGENTS).where(conditions).awaitSingle().value1() ?: 0

    val agents =
      dslContext
        .select(
          AGENTS.ID,
          AGENTS.NAME,
          AGENTS.DESCRIPTION,
          AGENTS.ACTIVE,
          AGENTS.ACTIVE_CONFIGURATION_ID,
          AGENTS.LANGUAGE,
          AGENTS.AVATAR,
          AGENTS.CREATED_AT,
          AGENTS.UPDATED_AT,
          AGENT_CONFIGURATIONS.ID,
          AGENT_CONFIGURATIONS.AGENT_ID,
          AGENT_CONFIGURATIONS.VERSION,
          AGENT_CONFIGURATIONS.CONTEXT,
          AGENT_CONFIGURATIONS.LLM_PROVIDER,
          AGENT_CONFIGURATIONS.MODEL,
          AGENT_CONFIGURATIONS.TEMPERATURE,
          AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
          AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
          AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
          AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
          AGENT_CONFIGURATIONS.ERROR_MESSAGE,
          AGENT_CONFIGURATIONS.CREATED_AT,
          AGENT_CONFIGURATIONS.UPDATED_AT,
        )
        .from(AGENTS)
        .leftJoin(AGENT_CONFIGURATIONS)
        .on(AGENT_CONFIGURATIONS.ID.eq(AGENTS.ACTIVE_CONFIGURATION_ID))
        .where(conditions)
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map {
          AgentWithConfiguration(
            it.into(AGENTS).toAgent(),
            it.into(AGENT_CONFIGURATIONS).toAgentConfiguration(),
          )
        }
        .toList()

    return CountedList(total, agents)
  }

  suspend fun get(id: KUUID): AgentFull {
    return dslContext
      .select(
        AGENTS.ID,
        AGENTS.NAME,
        AGENTS.DESCRIPTION,
        AGENTS.ACTIVE,
        AGENTS.ACTIVE_CONFIGURATION_ID,
        AGENTS.LANGUAGE,
        AGENTS.AVATAR,
        AGENTS.CREATED_AT,
        AGENTS.UPDATED_AT,
        AGENT_CONFIGURATIONS.ID,
        AGENT_CONFIGURATIONS.AGENT_ID,
        AGENT_CONFIGURATIONS.VERSION,
        AGENT_CONFIGURATIONS.CONTEXT,
        AGENT_CONFIGURATIONS.LLM_PROVIDER,
        AGENT_CONFIGURATIONS.MODEL,
        AGENT_CONFIGURATIONS.TEMPERATURE,
        AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
        AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
        AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
        AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        AGENT_CONFIGURATIONS.CREATED_AT,
        AGENT_CONFIGURATIONS.UPDATED_AT,
      )
      .from(AGENTS)
      .leftJoin(AGENT_CONFIGURATIONS)
      .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
      .where(AGENTS.ID.eq(id))
      .asFlow()
      .firstOrNull()
      ?.let { record ->
        val agent = record.into(AGENTS).toAgent()
        val configuration = record.into(AGENT_CONFIGURATIONS).toAgentConfiguration()
        val collections = getCollections(id)
        AgentFull(agent, configuration, collections)
      } ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id'")
  }

  suspend fun getAgent(agentId: KUUID): Agent {
    return dslContext
      .select(
        AGENTS.ID,
        AGENTS.NAME,
        AGENTS.DESCRIPTION,
        AGENTS.ACTIVE,
        AGENTS.ACTIVE_CONFIGURATION_ID,
        AGENTS.LANGUAGE,
        AGENTS.AVATAR,
        AGENTS.CREATED_AT,
        AGENTS.UPDATED_AT,
      )
      .from(AGENTS)
      .where(AGENTS.ID.eq(agentId))
      .awaitSingle()
      ?.into(AGENTS)
      ?.toAgent() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$agentId'")
  }

  suspend fun getActive(id: KUUID): Agent {
    return dslContext
      .select(
        AGENTS.ID,
        AGENTS.NAME,
        AGENTS.DESCRIPTION,
        AGENTS.ACTIVE,
        AGENTS.ACTIVE_CONFIGURATION_ID,
        AGENTS.LANGUAGE,
        AGENTS.AVATAR,
        AGENTS.CREATED_AT,
        AGENTS.UPDATED_AT,
      )
      .from(AGENTS)
      .where(AGENTS.ID.eq(id).and(AGENTS.ACTIVE.isTrue))
      .awaitFirstOrNull()
      ?.into(AGENTS)
      ?.toAgent() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id'")
  }

  suspend fun create(create: CreateAgent): AgentWithConfiguration {
    return dslContext.transactionCoroutine { tx ->
      val context = DSL.using(tx)
      val agentInit =
        context
          .insertInto(AGENTS)
          .set(AGENTS.NAME, create.name)
          .set(AGENTS.DESCRIPTION, create.description)
          .set(AGENTS.LANGUAGE, create.language)
          .set(AGENTS.ACTIVE, create.active)
          .returning()
          .awaitSingle()
          .into(AGENTS)
          .toAgent()

      val config =
        context
          .insertInto(AGENT_CONFIGURATIONS)
          .set(AGENT_CONFIGURATIONS.AGENT_ID, agentInit.id)
          .set(AGENT_CONFIGURATIONS.VERSION, 1)
          .set(AGENT_CONFIGURATIONS.CONTEXT, create.configuration.context)
          .set(AGENT_CONFIGURATIONS.LLM_PROVIDER, create.configuration.llmProvider)
          .set(AGENT_CONFIGURATIONS.MODEL, create.configuration.model)
          .set(AGENT_CONFIGURATIONS.TEMPERATURE, create.configuration.temperature)
          .set(AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS, create.configuration.maxCompletionTokens)
          .set(AGENT_CONFIGURATIONS.PRESENCE_PENALTY, create.configuration.presencePenalty)
          .set(
            AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
            create.configuration.instructions?.titleInstruction,
          )
          .set(
            AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
            create.configuration.instructions?.summaryInstruction,
          )
          .set(AGENT_CONFIGURATIONS.ERROR_MESSAGE, create.configuration.instructions?.errorMessage)
          .returning()
          .awaitSingle()
          .into(AGENT_CONFIGURATIONS)
          .toAgentConfiguration()

      val agent =
        context
          .update(AGENTS)
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, config.id)
          .where(AGENTS.ID.eq(agentInit.id))
          .returning()
          .awaitSingle()
          .into(AGENTS)
          .toAgent()

      return@transactionCoroutine AgentWithConfiguration(agent, config)
    }
  }

  suspend fun update(id: KUUID, update: UpdateAgent): AgentWithConfiguration {
    val currentConfiguration =
      dslContext
        .select(AGENT_CONFIGURATIONS.asterisk())
        .from(AGENT_CONFIGURATIONS)
        .join(AGENTS)
        .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
        .where(AGENTS.ID.eq(id))
        .awaitSingle()
        ?.into(AGENT_CONFIGURATIONS)
        ?.toAgentConfiguration()
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id' does not exist")

    return dslContext.transactionCoroutine { tx ->
      val context = DSL.using(tx)

      // TODO: Move this to service layer
      val configuration =
        // if configuration or instructions are updated, create a new version
        if (
          update.configuration != null &&
            isConfigurationDifferent(currentConfiguration, update.configuration)
        ) {
          // get the total number of versions
          val count =
            context
              .selectCount()
              .from(AGENT_CONFIGURATIONS)
              .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(id))
              .awaitSingle()
              .value1() ?: 1

          dslContext
            .insertInto(AGENT_CONFIGURATIONS)
            .set(AGENT_CONFIGURATIONS.AGENT_ID, id)
            .set(AGENT_CONFIGURATIONS.VERSION, count + 1)
            .set(AGENT_CONFIGURATIONS.CONTEXT, update.configuration.context)
            .set(AGENT_CONFIGURATIONS.LLM_PROVIDER, update.configuration.llmProvider)
            .set(AGENT_CONFIGURATIONS.MODEL, update.configuration.model)
            .set(AGENT_CONFIGURATIONS.TEMPERATURE, update.configuration.temperature)
            .set(
              AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
              update.configuration.maxCompletionTokens,
            )
            .set(AGENT_CONFIGURATIONS.PRESENCE_PENALTY, update.configuration.presencePenalty)
            .set(
              AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
              update.configuration.instructions?.titleInstruction
                ?: currentConfiguration.agentInstructions.titleInstruction,
            )
            .set(
              AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
              update.configuration.instructions?.summaryInstruction
                ?: currentConfiguration.agentInstructions.summaryInstruction,
            )
            .set(
              AGENT_CONFIGURATIONS.ERROR_MESSAGE,
              update.configuration.instructions?.errorMessage
                ?: currentConfiguration.agentInstructions.errorMessage,
            )
            .returning()
            .awaitSingle()
            .into(AGENT_CONFIGURATIONS)
            .toAgentConfiguration()
        } else {
          currentConfiguration
        }

      val agent =
        context
          .update(AGENTS)
          .set(AGENTS.NAME, DSL.coalesce(DSL.`val`(update.name), AGENTS.NAME))
          .set(AGENTS.DESCRIPTION, DSL.coalesce(DSL.`val`(update.description), AGENTS.DESCRIPTION))
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, configuration.id)
          .set(AGENTS.ACTIVE, DSL.coalesce(DSL.`val`(update.active), AGENTS.ACTIVE))
          .set(AGENTS.LANGUAGE, DSL.coalesce(DSL.`val`(update.language), AGENTS.LANGUAGE))
          .where(AGENTS.ID.eq(id))
          .returning()
          .awaitSingle()
          .into(AGENTS)
          .toAgent()

      return@transactionCoroutine AgentWithConfiguration(agent, configuration)
    }
  }

  private fun isConfigurationDifferent(
    current: AgentConfiguration,
    update: UpdateAgentConfiguration,
  ): Boolean {
    return update.context != null && update.context != current.context ||
      update.llmProvider != null && update.llmProvider != current.llmProvider ||
      update.model != null && update.model != current.model ||
      update.temperature != null && update.temperature != current.temperature ||
      update.maxCompletionTokens != null &&
        update.maxCompletionTokens != current.maxCompletionTokens ||
      update.presencePenalty != null && update.presencePenalty != current.presencePenalty ||
      update.instructions != null &&
        (update.instructions.titleInstruction != current.agentInstructions.titleInstruction ||
          update.instructions.summaryInstruction != current.agentInstructions.summaryInstruction ||
          update.instructions.errorMessage != current.agentInstructions.errorMessage)
  }

  suspend fun delete(id: KUUID): Int {
    return dslContext
      .deleteFrom(AGENTS)
      .where(AGENTS.ID.eq(id).and(AGENTS.ACTIVE.isFalse))
      .awaitSingle()
  }

  suspend fun updateAvatar(id: KUUID, avatarPath: String) {
    dslContext.update(AGENTS).set(AGENTS.AVATAR, avatarPath).where(AGENTS.ID.eq(id)).awaitSingle()
  }

  suspend fun removeAvatar(id: KUUID) {
    dslContext.update(AGENTS).setNull(AGENTS.AVATAR).where(AGENTS.ID.eq(id)).awaitSingle()
  }

  suspend fun updateCollections(
    agentId: KUUID,
    add: List<CollectionInsert>?,
    remove: List<CollectionRemove>?,
  ) {
    dslContext.transactionCoroutine { tx ->
      add?.let { additions ->
        if (additions.isEmpty()) {
          return@let
        }

        try {
          tx
            .dsl()
            .insertInto(
              AGENT_COLLECTIONS,
              AGENT_COLLECTIONS.AGENT_ID,
              AGENT_COLLECTIONS.COLLECTION,
              AGENT_COLLECTIONS.EMBEDDING_PROVIDER,
              AGENT_COLLECTIONS.EMBEDDING_MODEL,
              AGENT_COLLECTIONS.VECTOR_PROVIDER,
              AGENT_COLLECTIONS.AMOUNT,
              AGENT_COLLECTIONS.INSTRUCTION,
            )
            .apply {
              additions.forEach { collection ->
                values(
                  agentId,
                  collection.info.name,
                  collection.info.embeddingProvider,
                  collection.info.embeddingModel,
                  collection.info.vectorProvider,
                  collection.amount,
                  collection.instruction,
                )
              }
            }
            .onConflict(
              AGENT_COLLECTIONS.AGENT_ID,
              AGENT_COLLECTIONS.COLLECTION,
              AGENT_COLLECTIONS.VECTOR_PROVIDER,
            )
            .doUpdate()
            .set(AGENT_COLLECTIONS.AMOUNT, excluded(AGENT_COLLECTIONS.AMOUNT))
            .set(AGENT_COLLECTIONS.INSTRUCTION, excluded(AGENT_COLLECTIONS.INSTRUCTION))
            .awaitLast()
        } catch (e: DataAccessException) {
          LOG.error("Error adding collections", e)
          throw AppError.internal(e.message ?: "Failed to add collections")
        }
      }

      remove?.let { removals ->
        if (removals.isEmpty()) {
          return@let
        }

        try {
          tx
            .dsl()
            .deleteFrom(AGENT_COLLECTIONS)
            .where(
              DSL.or(
                removals.map { collection ->
                  AGENT_COLLECTIONS.AGENT_ID.eq(agentId)
                    .and(AGENT_COLLECTIONS.COLLECTION.eq(collection.name))
                    .and(AGENT_COLLECTIONS.VECTOR_PROVIDER.eq(collection.provider))
                }
              )
            )
            .awaitLast()
        } catch (e: DataAccessException) {
          LOG.error("Error removing collections", e)
          throw AppError.internal(e.message ?: "Failed to remove collections")
        }
      }
    }
  }

  suspend fun removeCollectionFromAll(collectionName: String, provider: String) {
    dslContext
      .deleteFrom(AGENT_COLLECTIONS)
      .where(
        AGENT_COLLECTIONS.COLLECTION.eq(collectionName)
          .and(AGENT_COLLECTIONS.VECTOR_PROVIDER.eq(provider))
      )
      .awaitSingle()
  }

  private suspend fun getCollections(id: KUUID): List<AgentCollection> {
    return dslContext
      .select(
        AGENT_COLLECTIONS.ID,
        AGENT_COLLECTIONS.AGENT_ID,
        AGENT_COLLECTIONS.INSTRUCTION,
        AGENT_COLLECTIONS.COLLECTION,
        AGENT_COLLECTIONS.AMOUNT,
        AGENT_COLLECTIONS.EMBEDDING_PROVIDER,
        AGENT_COLLECTIONS.EMBEDDING_MODEL,
        AGENT_COLLECTIONS.VECTOR_PROVIDER,
        AGENT_COLLECTIONS.CREATED_AT,
        AGENT_COLLECTIONS.UPDATED_AT,
      )
      .from(AGENT_COLLECTIONS)
      .where(AGENT_COLLECTIONS.AGENT_ID.eq(id))
      .asFlow()
      .map { it.into(AGENT_COLLECTIONS).toAgentCollection() }
      .toList()
  }

  suspend fun getAgentCounts(): AgentCounts {
    val total: Int = dslContext.selectCount().from(AGENTS).awaitSingle().value1() ?: 0

    val active: Int =
      dslContext.selectCount().from(AGENTS).where(AGENTS.ACTIVE.isTrue).awaitSingle().value1() ?: 0

    val inactive = total - active

    val counts =
      dslContext
        .select(AGENT_CONFIGURATIONS.LLM_PROVIDER, DSL.count())
        .from(AGENTS)
        .leftJoin(AGENT_CONFIGURATIONS)
        .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
        .where(AGENTS.ACTIVE.isTrue)
        .groupBy(AGENT_CONFIGURATIONS.LLM_PROVIDER)
        .asFlow()
        .toList()

    val out = mutableMapOf<String, Int>()

    for ((agent, count) in counts) {
      out[agent!!] = count
    }

    return AgentCounts(total, active, inactive, out)
  }

  suspend fun getAgentConfigurationVersions(
    agentId: KUUID,
    pagination: PaginationSort,
  ): CountedList<AgentConfiguration> {
    // No point in sorting by anything else
    val order =
      if (pagination.sorting().second == SortOrder.ASC) {
        AGENT_CONFIGURATIONS.CREATED_AT.asc()
      } else {
        AGENT_CONFIGURATIONS.CREATED_AT.desc()
      }
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENT_CONFIGURATIONS)
        .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(agentId))
        .awaitSingle()
        .value1() ?: 0

    val configurations =
      dslContext
        .select(
          AGENT_CONFIGURATIONS.ID,
          AGENT_CONFIGURATIONS.AGENT_ID,
          AGENT_CONFIGURATIONS.VERSION,
          AGENT_CONFIGURATIONS.CONTEXT,
          AGENT_CONFIGURATIONS.LLM_PROVIDER,
          AGENT_CONFIGURATIONS.MODEL,
          AGENT_CONFIGURATIONS.TEMPERATURE,
          AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
          AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
          AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
          AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
          AGENT_CONFIGURATIONS.ERROR_MESSAGE,
          AGENT_CONFIGURATIONS.CREATED_AT,
          AGENT_CONFIGURATIONS.UPDATED_AT,
        )
        .from(AGENT_CONFIGURATIONS)
        .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(agentId))
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { it.into(AGENT_CONFIGURATIONS).toAgentConfiguration() }
        .toList()

    return CountedList(total, configurations)
  }

  suspend fun getAgentConfiguration(agentId: KUUID, versionId: KUUID): AgentConfiguration {
    return dslContext
      .select(
        AGENT_CONFIGURATIONS.ID,
        AGENT_CONFIGURATIONS.AGENT_ID,
        AGENT_CONFIGURATIONS.VERSION,
        AGENT_CONFIGURATIONS.CONTEXT,
        AGENT_CONFIGURATIONS.LLM_PROVIDER,
        AGENT_CONFIGURATIONS.MODEL,
        AGENT_CONFIGURATIONS.TEMPERATURE,
        AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
        AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
        AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
        AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        AGENT_CONFIGURATIONS.CREATED_AT,
        AGENT_CONFIGURATIONS.UPDATED_AT,
      )
      .from(AGENT_CONFIGURATIONS)
      .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(agentId).and(AGENT_CONFIGURATIONS.ID.eq(versionId)))
      .awaitSingle()
      ?.into(AGENT_CONFIGURATIONS)
      ?.toAgentConfiguration()
      ?: throw AppError.api(
        ErrorReason.EntityDoesNotExist,
        "Agent configuration with ID '$versionId'",
      )
  }

  suspend fun rollbackVersion(agentId: KUUID, versionId: KUUID): AgentWithConfiguration {
    return dslContext.transactionCoroutine { tx ->
      val context = DSL.using(tx)

      val configuration = getAgentConfiguration(agentId, versionId)

      val agent =
        context
          .update(AGENTS)
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, versionId)
          .where(AGENTS.ID.eq(agentId))
          .returning()
          .awaitSingle()
          .into(AGENTS)
          .toAgent()

      return@transactionCoroutine AgentWithConfiguration(agent, configuration)
    }
  }

  private fun getSortOrderAgent(
    pagination: PaginationSort,
    admin: Boolean = false,
  ): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "name" -> AGENTS.NAME
        "description" -> AGENTS.DESCRIPTION
        "context" -> if (admin) AGENT_CONFIGURATIONS.CONTEXT else AGENTS.NAME
        "llmProvider" -> if (admin) AGENT_CONFIGURATIONS.LLM_PROVIDER else AGENTS.NAME
        "createdAt" -> AGENTS.CREATED_AT
        "updatedAt" -> AGENTS.UPDATED_AT
        "active" -> AGENTS.ACTIVE
        else -> AGENTS.NAME
      }

    val order =
      if (sortOrder == SortOrder.DESC) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return order
  }

  suspend fun getAgentTools(agentId: KUUID): List<AgentTool> {
    return dslContext
      .selectFrom(AGENT_TOOLS)
      .where(AGENT_TOOLS.AGENT_ID.eq(agentId))
      .asFlow()
      .map { it.toAgentTool() }
      .toList()
  }

  suspend fun updateAgentTools(agentId: KUUID, update: UpdateTools) {
    for (tool in update.add) {
      dslContext
        .insertInto(AGENT_TOOLS)
        .set(AGENT_TOOLS.AGENT_ID, agentId)
        .set(AGENT_TOOLS.TOOL_NAME, tool)
        .returning()
        .awaitSingle()
        .toAgentTool()
    }
    for (tool in update.remove) {
      dslContext
        .deleteFrom(AGENT_TOOLS)
        .where(AGENT_TOOLS.AGENT_ID.eq(agentId))
        .and(AGENT_TOOLS.TOOL_NAME.eq(tool))
        .awaitSingle()
    }
  }

  // Message Tool Calls methods
  suspend fun recordToolCall(
    messageId: KUUID,
    toolName: String,
    arguments: String,
    result: String,
  ): AgentToolCall {
    return dslContext
      .insertInto(AGENT_TOOL_CALLS)
      .set(AGENT_TOOL_CALLS.MESSAGE_ID, messageId)
      .set(AGENT_TOOL_CALLS.TOOL_NAME, toolName)
      .set(AGENT_TOOL_CALLS.TOOL_ARGUMENTS, arguments)
      .set(AGENT_TOOL_CALLS.TOOL_RESULT, result)
      .returning()
      .awaitSingle()
      .toAgentToolCall()
  }

  suspend fun getMessageToolCalls(messageId: KUUID): List<AgentToolCall> {
    return dslContext
      .selectFrom(AGENT_TOOL_CALLS)
      .where(AGENT_TOOL_CALLS.MESSAGE_ID.eq(messageId))
      .asFlow()
      .map { it.toAgentToolCall() }
      .toList()
  }
}

private fun SearchFiltersAdminAgents.generateConditions(): Condition {
  val nameCondition = name?.let { AGENTS.NAME.containsIgnoreCase(name) } ?: DSL.noCondition()
  val activeCondition = if (active == null) DSL.noCondition() else AGENTS.ACTIVE.eq(active)

  return DSL.and(nameCondition, activeCondition)
}
