package net.barrage.llmao.core.repository

import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toCollectionParams
import net.barrage.llmao.core.types.KUUID
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

  fun get(id: KUUID): Agent? {
    return dslContext.selectFrom(AGENTS).where(AGENTS.ID.eq(id)).fetchOne(AgentsRecord::toAgent)
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

  fun getCollections(id: KUUID): List<Pair<String, Int>> {
    val collections =
      dslContext
        .selectFrom(AGENT_COLLECTIONS)
        .where(AGENT_COLLECTIONS.AGENT_ID.eq(id))
        .fetchInto(AgentCollectionsRecord::class.java)
    return collections.map { it.toCollectionParams() }
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
