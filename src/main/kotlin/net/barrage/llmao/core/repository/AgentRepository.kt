package net.barrage.llmao.core.repository

import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentCollection
import net.barrage.llmao.core.models.AgentWithCollections
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toCollection
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.records.AgentCollectionsRecord
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import org.jooq.SortField
import org.jooq.impl.DSL

class AgentRepository {
  fun getAll(pagination: PaginationSort, showDeactivated: Boolean): CountedList<Agent> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(AGENTS)
        .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else DSL.noCondition())
        .fetchOne(0, Int::class.java)!!

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

  fun get(id: KUUID): AgentWithCollections {
    val agent =
      dslContext.selectFrom(AGENTS).where(AGENTS.ID.eq(id)).fetchOne(AgentsRecord::toAgent)
        ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent with ID '$id'")

    val collections = getCollections(id)

    return AgentWithCollections(agent, collections)
  }

  fun create(newAgent: CreateAgent): Agent? {
    return dslContext
      .insertInto(AGENTS)
      .set(AGENTS.NAME, newAgent.name)
      .set(AGENTS.CONTEXT, newAgent.context)
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun update(id: KUUID, update: UpdateAgent): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.NAME, DSL.coalesce(DSL.`val`(update.name), AGENTS.NAME))
      .set(AGENTS.DESCRIPTION, DSL.coalesce(DSL.`val`(update.description), AGENTS.DESCRIPTION))
      .set(AGENTS.CONTEXT, DSL.coalesce(DSL.`val`(update.context), AGENTS.CONTEXT))
      .set(AGENTS.LLM_PROVIDER, DSL.coalesce(DSL.`val`(update.llmProvider), AGENTS.LLM_PROVIDER))
      .set(AGENTS.MODEL, DSL.coalesce(DSL.`val`(update.model), AGENTS.MODEL))
      .set(AGENTS.TEMPERATURE, DSL.coalesce(DSL.`val`(update.temperature), AGENTS.TEMPERATURE))
      .set(
        AGENTS.VECTOR_PROVIDER,
        DSL.coalesce(DSL.`val`(update.vectorProvider), AGENTS.VECTOR_PROVIDER),
      )
      .set(AGENTS.LANGUAGE, DSL.coalesce(DSL.`val`(update.language?.language), AGENTS.LANGUAGE))
      .set(AGENTS.ACTIVE, DSL.coalesce(DSL.`val`(update.active), AGENTS.ACTIVE))
      .set(
        AGENTS.EMBEDDING_PROVIDER,
        DSL.coalesce(DSL.`val`(update.embeddingProvider), AGENTS.EMBEDDING_PROVIDER),
      )
      .set(
        AGENTS.EMBEDDING_MODEL,
        DSL.coalesce(DSL.`val`(update.embeddingModel), AGENTS.EMBEDDING_MODEL),
      )
      .where(AGENTS.ID.eq(id))
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun updateCollections(agentId: KUUID, update: UpdateCollections) {
    val insertQuery =
      dslContext.batch(
        update.add.map { (name, amount) ->
          dslContext
            .insertInto(AGENT_COLLECTIONS)
            .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
            .set(AGENT_COLLECTIONS.COLLECTION, name)
            .set(AGENT_COLLECTIONS.AMOUNT, amount)
            .onConflict(AGENT_COLLECTIONS.AGENT_ID, AGENT_COLLECTIONS.COLLECTION)
            .doUpdate()
            .set(AGENT_COLLECTIONS.AMOUNT, amount)
        }
      )

    insertQuery.execute()

    dslContext
      .deleteFrom(AGENT_COLLECTIONS)
      .where(
        AGENT_COLLECTIONS.AGENT_ID.eq(agentId).and(AGENT_COLLECTIONS.COLLECTION.`in`(update.remove))
      )
      .execute()
  }

  fun getCollections(id: KUUID): List<AgentCollection> {
    val collections =
      dslContext
        .selectFrom(AGENT_COLLECTIONS)
        .where(AGENT_COLLECTIONS.AGENT_ID.eq(id))
        .fetchInto(AgentCollectionsRecord::class.java)
    return collections.map { it.toCollection() }
  }

  private fun getSortOrder(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "name" -> AGENTS.NAME
        "description" -> AGENTS.DESCRIPTION
        "context" -> AGENTS.CONTEXT
        "llmProvider" -> AGENTS.LLM_PROVIDER
        "createdAt" -> AGENTS.CREATED_AT
        "updatedAt" -> AGENTS.UPDATED_AT
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
