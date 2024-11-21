package net.barrage.llmao.core.repository

import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.AgentCounts
import net.barrage.llmao.core.models.AgentFull
import net.barrage.llmao.core.models.AgentWithConfiguration
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toAgentCollection
import net.barrage.llmao.core.models.toAgentConfiguration
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.records.AgentCollectionsRecord
import net.barrage.llmao.tables.records.AgentConfigurationsRecord
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class AgentRepository(private val dslContext: DSLContext) {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    val order = getSortOrderAgent(pagination, admin = false)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .fetchOne(0, Int::class.java) ?: 0

    val agents =
      dslContext
        .selectFrom(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch(AgentsRecord::toAgent)

    return CountedList(total, agents)
  }

  fun getAllAdmin(
    pagination: PaginationSort,
    showDeactivated: Boolean,
  ): CountedList<AgentWithConfiguration> {
    val order = getSortOrderAgent(pagination, admin = true)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .fetchOne(0, Int::class.java) ?: 0

    val agents =
      dslContext
        .select()
        .from(AGENTS)
        .leftJoin(AGENT_CONFIGURATIONS)
        .on(AGENT_CONFIGURATIONS.ID.eq(AGENTS.ACTIVE_CONFIGURATION_ID))
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch()
        .map {
          AgentWithConfiguration(
            it.into(AGENTS).toAgent(),
            it.into(AGENT_CONFIGURATIONS).toAgentConfiguration(),
          )
        }

    return CountedList(total, agents)
  }

  fun get(id: KUUID): AgentFull {
    return dslContext
      .select(AGENTS.asterisk(), AGENT_CONFIGURATIONS.asterisk())
      .from(AGENTS)
      .leftJoin(AGENT_CONFIGURATIONS)
      .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
      .where(AGENTS.ID.eq(id))
      .fetchOne { record ->
        val agent = record.into(AGENTS).toAgent()
        val configuration = record.into(AGENT_CONFIGURATIONS).toAgentConfiguration()
        val collections = getCollections(id)
        AgentFull(agent, configuration, collections)
      } ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id'")
  }

  fun getActive(id: KUUID): Agent {
    return dslContext
      .selectFrom(AGENTS)
      .where(AGENTS.ID.eq(id).and(AGENTS.ACTIVE.isTrue))
      .fetchOne(AgentsRecord::toAgent)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id'")
  }

  fun create(create: CreateAgent): AgentWithConfiguration? {
    return dslContext.transactionResult { tx ->
      val context = DSL.using(tx)
      val agentInit =
        context
          .insertInto(AGENTS)
          .set(AGENTS.NAME, create.name)
          .set(AGENTS.DESCRIPTION, create.description)
          .set(AGENTS.VECTOR_PROVIDER, create.vectorProvider)
          .set(AGENTS.EMBEDDING_PROVIDER, create.embeddingProvider)
          .set(AGENTS.EMBEDDING_MODEL, create.embeddingModel)
          .set(AGENTS.LANGUAGE, create.language)
          .set(AGENTS.ACTIVE, create.active)
          .returning()
          .fetchOne(AgentsRecord::toAgent)!!

      val config =
        context
          .insertInto(AGENT_CONFIGURATIONS)
          .set(AGENT_CONFIGURATIONS.AGENT_ID, agentInit.id)
          .set(AGENT_CONFIGURATIONS.VERSION, 1)
          .set(AGENT_CONFIGURATIONS.CONTEXT, create.configuration.context)
          .set(AGENT_CONFIGURATIONS.LLM_PROVIDER, create.configuration.llmProvider)
          .set(AGENT_CONFIGURATIONS.MODEL, create.configuration.model)
          .set(AGENT_CONFIGURATIONS.TEMPERATURE, create.configuration.temperature)
          .set(
            AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
            create.configuration.instructions?.titleInstruction,
          )
          .set(
            AGENT_CONFIGURATIONS.LANGUAGE_INSTRUCTION,
            create.configuration.instructions?.languageInstruction,
          )
          .set(
            AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
            create.configuration.instructions?.summaryInstruction,
          )
          .set(AGENT_CONFIGURATIONS.VERSION, 0)
          .returning()
          .fetchOne(AgentConfigurationsRecord::toAgentConfiguration)!!

      val agent =
        context
          .update(AGENTS)
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, config.id)
          .where(AGENTS.ID.eq(agentInit.id))
          .returning()
          .fetchOne(AgentsRecord::toAgent)!!

      return@transactionResult AgentWithConfiguration(agent, config)
    }
  }

  fun update(id: KUUID, update: UpdateAgent): AgentWithConfiguration {
    return dslContext.transactionResult { tx ->
      val context = DSL.using(tx)

      val currentConfiguration =
        context
          .select(AGENT_CONFIGURATIONS.asterisk())
          .from(AGENT_CONFIGURATIONS)
          .join(AGENTS)
          .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
          .where(AGENTS.ID.eq(id))
          .fetchOne()
          ?.into(AGENT_CONFIGURATIONS)
          ?.toAgentConfiguration()
          ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent configuration with ID '$id'")

      val configuration =
        // if configuration or instructions are updated, create a new version
        if (update.configuration != null) {
          // get the total number of versions
          val count =
            context
              .selectCount()
              .from(AGENT_CONFIGURATIONS)
              .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(id))
              .fetchOne(0, Int::class.java) ?: 0

          dslContext
            .insertInto(AGENT_CONFIGURATIONS)
            .set(AGENT_CONFIGURATIONS.AGENT_ID, id)
            .set(AGENT_CONFIGURATIONS.VERSION, count + 1)
            .set(AGENT_CONFIGURATIONS.CONTEXT, update.configuration.context)
            .set(AGENT_CONFIGURATIONS.LLM_PROVIDER, update.configuration.llmProvider)
            .set(AGENT_CONFIGURATIONS.MODEL, update.configuration.model)
            .set(AGENT_CONFIGURATIONS.TEMPERATURE, update.configuration.temperature)
            .set(
              AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
              update.configuration.instructions?.titleInstruction
                ?: currentConfiguration.agentInstructions.titleInstruction,
            )
            .set(
              AGENT_CONFIGURATIONS.LANGUAGE_INSTRUCTION,
              update.configuration.instructions?.languageInstruction
                ?: currentConfiguration.agentInstructions.languageInstruction,
            )
            .set(
              AGENT_CONFIGURATIONS.SUMMARY_INSTRUCTION,
              update.configuration.instructions?.summaryInstruction
                ?: currentConfiguration.agentInstructions.summaryInstruction,
            )
            .returning()
            .fetchOne(AgentConfigurationsRecord::toAgentConfiguration)!!
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
          .fetchOne(AgentsRecord::toAgent)!!

      return@transactionResult AgentWithConfiguration(agent, configuration)
    }
  }

  fun updateCollections(agentId: KUUID, update: UpdateCollections) {
    update.add?.let {
      dslContext
        .batch(
          it.map { (name, amount, instruction) ->
            dslContext
              .insertInto(AGENT_COLLECTIONS)
              .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
              .set(AGENT_COLLECTIONS.COLLECTION, name)
              .set(AGENT_COLLECTIONS.AMOUNT, amount)
              .set(AGENT_COLLECTIONS.INSTRUCTION, instruction)
              .onConflict(AGENT_COLLECTIONS.AGENT_ID, AGENT_COLLECTIONS.COLLECTION)
              .doUpdate()
              .set(AGENT_COLLECTIONS.AMOUNT, amount)
          }
        )
        .execute()
    }

    update.remove?.let {
      dslContext
        .deleteFrom(AGENT_COLLECTIONS)
        .where(AGENT_COLLECTIONS.AGENT_ID.eq(agentId).and(AGENT_COLLECTIONS.COLLECTION.`in`(it)))
        .execute()
    }
  }

  private fun getCollections(id: KUUID): List<AgentCollection> {
    return dslContext
      .selectFrom(AGENT_COLLECTIONS)
      .where(AGENT_COLLECTIONS.AGENT_ID.eq(id))
      .fetchInto(AgentCollectionsRecord::class.java)
      .map { it.toAgentCollection() }
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

  fun getAgentCounts(): AgentCounts {
    val total: Int = dslContext.selectCount().from(AGENTS).fetchOne(0, Int::class.java) ?: 0

    val active: Int =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(AGENTS.ACTIVE.isTrue)
        .groupBy(AGENTS.ACTIVE)
        .fetchOne(0, Int::class.java) ?: 0

    val inactive = total - active

    val counts =
      dslContext
        .select(AGENT_CONFIGURATIONS.LLM_PROVIDER, DSL.count())
        .from(AGENTS)
        .leftJoin(AGENT_CONFIGURATIONS)
        .on(AGENTS.ACTIVE_CONFIGURATION_ID.eq(AGENT_CONFIGURATIONS.ID))
        .where(AGENTS.ACTIVE.isTrue)
        .groupBy(AGENT_CONFIGURATIONS.LLM_PROVIDER)
        .fetch { it }

    val out = mutableMapOf<String, Int>()

    for ((agent, count) in counts) {
      out[agent!!] = count
    }

    return AgentCounts(total, active, inactive, out)
  }

  fun getAgentConfigurationVersions(
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
        .fetchOne(0, Int::class.java) ?: 0

    val configurations =
      dslContext
        .selectFrom(AGENT_CONFIGURATIONS)
        .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(agentId))
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch(AgentConfigurationsRecord::toAgentConfiguration)

    return CountedList(total, configurations)
  }

  fun getAgentConfiguration(agentId: KUUID, versionId: KUUID): AgentConfiguration {
    return dslContext
      .selectFrom(AGENT_CONFIGURATIONS)
      .where(AGENT_CONFIGURATIONS.AGENT_ID.eq(agentId).and(AGENT_CONFIGURATIONS.ID.eq(versionId)))
      .fetchOne(AgentConfigurationsRecord::toAgentConfiguration)
      ?: throw AppError.api(
        ErrorReason.EntityDoesNotExist,
        "Agent configuration with ID '$versionId'",
      )
  }

  fun rollbackVersion(agentId: KUUID, versionId: KUUID): AgentWithConfiguration {
    return dslContext.transactionResult { tx ->
      val context = DSL.using(tx)

      val configuration = getAgentConfiguration(agentId, versionId)

      val agent =
        context
          .update(AGENTS)
          .set(AGENTS.ACTIVE_CONFIGURATION_ID, versionId)
          .where(AGENTS.ID.eq(agentId))
          .returning()
          .fetchOne(AgentsRecord::toAgent)!!

      return@transactionResult AgentWithConfiguration(agent, configuration)
    }
  }

  fun getAgent(agentId: KUUID): Agent {
    return dslContext
      .selectFrom(AGENTS)
      .where(AGENTS.ID.eq(agentId))
      .fetchOne(AgentsRecord::toAgent)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$agentId'")
  }
}
