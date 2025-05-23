package net.barrage.llmao.app.workflow.chat.repository

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitLast
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentCollection
import net.barrage.llmao.app.workflow.chat.model.AgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.AgentCounts
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.model.AgentGroupUpdate
import net.barrage.llmao.app.workflow.chat.model.AgentTool
import net.barrage.llmao.app.workflow.chat.model.AgentUpdateTools
import net.barrage.llmao.app.workflow.chat.model.AgentWithConfiguration
import net.barrage.llmao.app.workflow.chat.model.CollectionInsert
import net.barrage.llmao.app.workflow.chat.model.CollectionRemove
import net.barrage.llmao.app.workflow.chat.model.CreateAgent
import net.barrage.llmao.app.workflow.chat.model.SearchFiltersAdminAgents
import net.barrage.llmao.app.workflow.chat.model.UpdateAgent
import net.barrage.llmao.app.workflow.chat.model.UpdateAgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.toAgent
import net.barrage.llmao.app.workflow.chat.model.toAgentCollection
import net.barrage.llmao.app.workflow.chat.model.toAgentConfiguration
import net.barrage.llmao.app.workflow.chat.model.toAgentTool
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.database.set
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.model.common.SortOrder
import net.barrage.llmao.tables.records.AgentConfigurationsRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.AGENT_PERMISSIONS
import net.barrage.llmao.tables.references.AGENT_TOOLS
import net.barrage.llmao.types.KUUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.InsertSetMoreStep
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.DSL.excluded
import org.jooq.kotlin.coroutines.transactionCoroutine

class AgentRepository(private val dslContext: DSLContext) {
  suspend fun getAll(
    pagination: PaginationSort,
    showDeactivated: Boolean,
    groups: List<String>,
    modelsAvailable: List<String>,
  ): CountedList<Agent> {
    val order = getSortOrderAgent(pagination, admin = false)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENTS)
        .leftJoin(AGENT_PERMISSIONS)
        .on(AGENT_PERMISSIONS.AGENT_ID.eq(AGENTS.ID))
        .leftJoin(AGENT_CONFIGURATIONS)
        .on(AGENT_CONFIGURATIONS.ID.eq(AGENTS.ACTIVE_CONFIGURATION_ID))
        .where(
          (if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
            .and(AGENT_PERMISSIONS.GROUP.isNull.or(AGENT_PERMISSIONS.GROUP.`in`(groups)))
            .and(AGENT_CONFIGURATIONS.MODEL.`in`(modelsAvailable))
        )
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
        .leftJoin(AGENT_PERMISSIONS)
        .on(AGENT_PERMISSIONS.AGENT_ID.eq(AGENTS.ID))
        .leftJoin(AGENT_CONFIGURATIONS)
        .on(AGENT_CONFIGURATIONS.ID.eq(AGENTS.ACTIVE_CONFIGURATION_ID))
        .where(
          (if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition()).and(
            AGENT_PERMISSIONS.GROUP.isNull
              .or(AGENT_PERMISSIONS.GROUP.`in`(groups))
              .and(AGENT_CONFIGURATIONS.MODEL.`in`(modelsAvailable))
          )
        )
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

  suspend fun listAllFull(): List<AgentFull> {
    val agents = mutableMapOf<KUUID, AgentWithConfiguration>()
    val collections = mutableMapOf<KUUID, MutableList<AgentCollection>>()
    val groups = mutableMapOf<KUUID, MutableList<String>>()
    val tools = mutableMapOf<KUUID, MutableList<String>>()

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
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        AGENT_CONFIGURATIONS.CREATED_AT,
        AGENT_CONFIGURATIONS.UPDATED_AT,
        AGENT_COLLECTIONS.ID,
        AGENT_COLLECTIONS.AGENT_ID,
        AGENT_COLLECTIONS.INSTRUCTION,
        AGENT_COLLECTIONS.COLLECTION,
        AGENT_COLLECTIONS.AMOUNT,
        AGENT_COLLECTIONS.MAX_DISTANCE,
        AGENT_COLLECTIONS.EMBEDDING_PROVIDER,
        AGENT_COLLECTIONS.EMBEDDING_MODEL,
        AGENT_COLLECTIONS.VECTOR_PROVIDER,
        AGENT_COLLECTIONS.CREATED_AT,
        AGENT_COLLECTIONS.UPDATED_AT,
        AGENT_PERMISSIONS.GROUP,
        AGENT_PERMISSIONS.CREATED_BY,
        AGENT_TOOLS.TOOL_NAME,
      )
      .from(AGENTS)
      .leftJoin(AGENT_CONFIGURATIONS)
      .on(AGENT_CONFIGURATIONS.ID.eq(AGENTS.ACTIVE_CONFIGURATION_ID))
      .leftJoin(AGENT_COLLECTIONS)
      .on(AGENT_COLLECTIONS.AGENT_ID.eq(AGENTS.ID))
      .leftJoin(AGENT_PERMISSIONS)
      .on(AGENT_PERMISSIONS.AGENT_ID.eq(AGENTS.ID))
      .leftJoin(AGENT_TOOLS)
      .on(AGENT_TOOLS.AGENT_ID.eq(AGENTS.ID))
      .asFlow()
      .collect { record ->
        val agent = record.into(AGENTS).toAgent()
        val configuration = record.into(AGENT_CONFIGURATIONS).toAgentConfiguration()

        if (!agents.containsKey(agent.id)) {
          agents[agent.id] = AgentWithConfiguration(agent, configuration)
        }

        if (record.get<KUUID?>(AGENT_COLLECTIONS.ID) != null) {
          val collection = record.into(AGENT_COLLECTIONS).toAgentCollection()
          collections.computeIfAbsent(agent.id) { mutableListOf() }.add(collection)
        }

        if (record.get<String?>(AGENT_PERMISSIONS.GROUP) != null) {
          val group = record.into(AGENT_PERMISSIONS).group
          groups.computeIfAbsent(agent.id) { mutableListOf() }.add(group)
        }

        if (record.get<String?>(AGENT_TOOLS.TOOL_NAME) != null) {
          val tool = record.into(AGENT_TOOLS).toolName
          tools.computeIfAbsent(agent.id) { mutableListOf() }.add(tool)
        }
      }

    return agents.values.map {
      AgentFull(
        agent = it.agent,
        configuration = it.configuration,
        collections = collections[it.agent.id] ?: emptyList(),
        groups = groups[it.agent.id] ?: emptyList(),
        tools = tools[it.agent.id] ?: emptyList(),
      )
    }
  }

  suspend fun import(agents: List<AgentFull>) {
    dslContext.transactionCoroutine { tx ->
      val context = tx.dsl()

      for (agent in agents) {
        val agentId = agent.agent.id
        val configurationId = agent.configuration.id

        context
          .insertInto(AGENTS)
          .set(AGENTS.ID, agentId)
          .set(AGENTS.NAME, agent.agent.name)
          .set(AGENTS.DESCRIPTION, agent.agent.description)
          .set(AGENTS.ACTIVE, agent.agent.active)
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, configurationId)
          .set(AGENTS.LANGUAGE, agent.agent.language)
          .awaitSingle()

        context
          .insertInto(AGENT_CONFIGURATIONS)
          .set(AGENT_CONFIGURATIONS.ID, configurationId)
          .set(AGENT_CONFIGURATIONS.AGENT_ID, agentId)
          .set(AGENT_CONFIGURATIONS.VERSION, agent.configuration.version)
          .set(AGENT_CONFIGURATIONS.CONTEXT, agent.configuration.context)
          .set(AGENT_CONFIGURATIONS.LLM_PROVIDER, agent.configuration.llmProvider)
          .set(AGENT_CONFIGURATIONS.MODEL, agent.configuration.model)
          .set(AGENT_CONFIGURATIONS.TEMPERATURE, agent.configuration.temperature)
          .set(AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS, agent.configuration.maxCompletionTokens)
          .set(AGENT_CONFIGURATIONS.PRESENCE_PENALTY, agent.configuration.presencePenalty)
          .set(
            AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
            agent.configuration.agentInstructions.titleInstruction,
          )
          .set(
            AGENT_CONFIGURATIONS.ERROR_MESSAGE,
            agent.configuration.agentInstructions.errorMessage,
          )
          .awaitSingle()

        for (collection in agent.collections) {
          context
            .insertInto(AGENT_COLLECTIONS)
            .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
            .set(AGENT_COLLECTIONS.COLLECTION, collection.collection)
            .set(AGENT_COLLECTIONS.EMBEDDING_PROVIDER, collection.embeddingProvider)
            .set(AGENT_COLLECTIONS.EMBEDDING_MODEL, collection.embeddingModel)
            .set(AGENT_COLLECTIONS.VECTOR_PROVIDER, collection.vectorProvider)
            .set(AGENT_COLLECTIONS.AMOUNT, collection.amount)
            .set(AGENT_COLLECTIONS.INSTRUCTION, collection.instruction)
            .set(AGENT_COLLECTIONS.MAX_DISTANCE, collection.maxDistance)
            .awaitSingle()
        }

        for (tool in agent.tools) {
          context
            .insertInto(AGENT_TOOLS)
            .set(AGENT_TOOLS.AGENT_ID, agentId)
            .set(AGENT_TOOLS.TOOL_NAME, tool)
            .awaitSingle()
        }
      }
    }
  }

  /** Should be called from a chat context. */
  suspend fun userGet(id: KUUID, groups: List<String>): AgentFull? {
    val agentGroups =
      dslContext
        .select(AGENT_PERMISSIONS.GROUP)
        .from(AGENT_PERMISSIONS)
        .where(AGENT_PERMISSIONS.AGENT_ID.eq(id))
        .asFlow()
        .map { it.into(AGENT_PERMISSIONS).group }
        .toList()

    if (agentGroups.isNotEmpty() && agentGroups.none { it in groups }) {
      return null
    }

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
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        AGENT_CONFIGURATIONS.CREATED_AT,
        AGENT_CONFIGURATIONS.UPDATED_AT,
      )
      .from(AGENTS)
      .leftJoin(AGENT_CONFIGURATIONS)
      .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
      .where(AGENTS.ID.eq(id).and(AGENTS.ACTIVE.eq(true)))
      .awaitFirstOrNull()
      ?.let { record ->
        val agent = record.into(AGENTS).toAgent()
        val configuration = record.into(AGENT_CONFIGURATIONS).toAgentConfiguration()
        val collections = getCollections(id)
        val tools = getAgentTools(id)
        AgentFull(
          agent = agent,
          configuration = configuration,
          collections = collections,
          groups = agentGroups,
          tools = tools.map { it.toolName },
        )
      }
  }

  suspend fun get(id: KUUID): AgentFull? {
    val agentGroups =
      dslContext
        .select(AGENT_PERMISSIONS.GROUP)
        .from(AGENT_PERMISSIONS)
        .where(AGENT_PERMISSIONS.AGENT_ID.eq(id))
        .asFlow()
        .map { it.into(AGENT_PERMISSIONS).group }
        .toList()

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
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        AGENT_CONFIGURATIONS.CREATED_AT,
        AGENT_CONFIGURATIONS.UPDATED_AT,
      )
      .from(AGENTS)
      .leftJoin(AGENT_CONFIGURATIONS)
      .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
      .where(AGENTS.ID.eq(id))
      .awaitFirstOrNull()
      ?.let { record ->
        val agent = record.into(AGENTS).toAgent()
        val configuration = record.into(AGENT_CONFIGURATIONS).toAgentConfiguration()
        val collections = getCollections(id)
        val tools = getAgentTools(id)
        AgentFull(
          agent = agent,
          configuration = configuration,
          collections = collections,
          groups = agentGroups,
          tools = tools.map { it.toolName },
        )
      }
  }

  /** Should be called only from user context. */
  suspend fun getAgent(agentId: KUUID, groups: List<String>): Agent? {
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
      .leftJoin(AGENT_PERMISSIONS)
      .on(AGENT_PERMISSIONS.AGENT_ID.eq(AGENTS.ID))
      .where(
        AGENTS.ID.eq(agentId)
          .and(AGENT_PERMISSIONS.GROUP.`in`(groups).or(AGENT_PERMISSIONS.GROUP.isNull))
      )
      .awaitFirstOrNull()
      ?.into(AGENTS)
      ?.toAgent()
  }

  /** Get an agent unconditionally. Should be called only from admin context. */
  suspend fun getAgent(agentId: KUUID): Agent? {
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
      .awaitFirstOrNull()
      ?.into(AGENTS)
      ?.toAgent()
  }

  suspend fun getActive(id: KUUID): Agent? {
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
      ?.toAgent()
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
        .awaitFirstOrNull()
        ?.into(AGENT_CONFIGURATIONS)
        ?.toAgentConfiguration()
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id' does not exist")

    return dslContext.transactionCoroutine { tx ->
      val context = DSL.using(tx)

      // TODO: Move this check to service layer
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
            .apply { update.configuration.insertSet(this, currentConfiguration) }
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
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, configuration.id)
          .set(update.name, AGENTS.NAME)
          .set(update.description, AGENTS.DESCRIPTION)
          .set(update.active, AGENTS.ACTIVE)
          .set(update.language, AGENTS.LANGUAGE)
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
    val requiredChanged =
      (update.context != null && update.context != current.context ||
        update.llmProvider != null && update.llmProvider != current.llmProvider ||
        update.model != null && update.model != current.model ||
        update.temperature != null && update.temperature != current.temperature)

    val optionalChanged =
      (update.maxCompletionTokens !is PropertyUpdate.Undefined &&
        update.maxCompletionTokens.value() != current.maxCompletionTokens) ||
        (update.presencePenalty !is PropertyUpdate.Undefined &&
          update.presencePenalty.value() != current.presencePenalty) ||
        (update.instructions != null &&
          (update.instructions.titleInstruction !is PropertyUpdate.Undefined &&
            update.instructions.titleInstruction.value() !=
              current.agentInstructions.titleInstruction) &&
          (update.instructions.errorMessage !is PropertyUpdate.Undefined &&
            update.instructions.errorMessage.value() != current.agentInstructions.errorMessage))

    return requiredChanged || optionalChanged
  }

  suspend fun delete(id: KUUID): Int {
    return dslContext.deleteFrom(AGENTS).where(AGENTS.ID.eq(id)).awaitSingle()
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

  // AGENT AVATAR

  suspend fun updateAvatar(id: KUUID, avatarPath: String) {
    dslContext.update(AGENTS).set(AGENTS.AVATAR, avatarPath).where(AGENTS.ID.eq(id)).awaitSingle()
  }

  suspend fun removeAvatar(id: KUUID) {
    dslContext.update(AGENTS).setNull(AGENTS.AVATAR).where(AGENTS.ID.eq(id)).awaitSingle()
  }

  // AGENT COLLECTIONS

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
              AGENT_COLLECTIONS.MAX_DISTANCE,
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
                  collection.maxDistance,
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
            .set(AGENT_COLLECTIONS.MAX_DISTANCE, excluded(AGENT_COLLECTIONS.MAX_DISTANCE))
            .awaitLast()
        } catch (e: DataAccessException) {
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
          // LOG.error("Error removing collections", e)
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
        AGENT_COLLECTIONS.MAX_DISTANCE,
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

  // AGENT CONFIGURATION

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

  suspend fun updateGroups(agentId: KUUID, groups: AgentGroupUpdate) {
    dslContext.transactionCoroutine { tx ->
      val context = DSL.using(tx)

      groups.add?.let { add ->
        for (group in add) {
          context
            .insertInto(AGENT_PERMISSIONS)
            .set(AGENT_PERMISSIONS.AGENT_ID, agentId)
            .set(AGENT_PERMISSIONS.GROUP, group)
            .onConflict()
            .doNothing()
            .awaitSingle()
        }
      }

      groups.remove?.let { remove ->
        for (group in remove) {
          context
            .deleteFrom(AGENT_PERMISSIONS)
            .where(AGENT_PERMISSIONS.AGENT_ID.eq(agentId).and(AGENT_PERMISSIONS.GROUP.eq(group)))
            .awaitSingle()
        }
      }
    }
  }

  suspend fun getAgentTools(agentId: KUUID): List<AgentTool> {
    return dslContext
      .selectFrom(AGENT_TOOLS)
      .where(AGENT_TOOLS.AGENT_ID.eq(agentId))
      .asFlow()
      .map { it.toAgentTool() }
      .toList()
  }

  suspend fun updateAgentTools(agentId: KUUID, update: AgentUpdateTools) {
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
}

private fun SearchFiltersAdminAgents.generateConditions(): Condition {
  val nameCondition = name?.let { AGENTS.NAME.containsIgnoreCase(name) } ?: DSL.noCondition()
  val activeCondition = if (active == null) DSL.noCondition() else AGENTS.ACTIVE.eq(active)

  return DSL.and(nameCondition, activeCondition)
}

private fun UpdateAgentConfiguration.insertSet(
  statement: InsertSetMoreStep<AgentConfigurationsRecord>,
  currentConfiguration: AgentConfiguration,
): InsertSetMoreStep<AgentConfigurationsRecord> {
  var statement =
    statement
      .set(context, AGENT_CONFIGURATIONS.CONTEXT, currentConfiguration.context)
      .set(llmProvider, AGENT_CONFIGURATIONS.LLM_PROVIDER, currentConfiguration.llmProvider)
      .set(model, AGENT_CONFIGURATIONS.MODEL, currentConfiguration.model)
      .set(temperature, AGENT_CONFIGURATIONS.TEMPERATURE, currentConfiguration.temperature)
      .set(
        maxCompletionTokens,
        AGENT_CONFIGURATIONS.MAX_COMPLETION_TOKENS,
        defaultIfUndefined = currentConfiguration.maxCompletionTokens,
      )
      .set(
        presencePenalty,
        AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
        defaultIfUndefined = currentConfiguration.presencePenalty,
      )

  return instructions?.let {
    statement
      .set(
        instructions.titleInstruction,
        AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
        defaultIfUndefined = currentConfiguration.agentInstructions.titleInstruction,
      )
      .set(
        instructions.errorMessage,
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
        defaultIfUndefined = currentConfiguration.agentInstructions.errorMessage,
      )
  } ?: statement
}
