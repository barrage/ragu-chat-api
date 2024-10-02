package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import net.barrage.llmao.models.Agent
import net.barrage.llmao.models.CountedList
import net.barrage.llmao.models.CreateAgent
import net.barrage.llmao.models.PaginationSort
import net.barrage.llmao.models.SortOrder
import net.barrage.llmao.models.UpdateAgent
import net.barrage.llmao.models.toAgent
import net.barrage.llmao.models.toCollectionParams
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.serializers.KUUID
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

  fun update(id: KUUID, updated: UpdateAgent): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.NAME, DSL.coalesce(DSL.`val`(updated.name), AGENTS.NAME))
      .set(AGENTS.CONTEXT, DSL.coalesce(DSL.`val`(updated.context), AGENTS.CONTEXT))
      .where(AGENTS.ID.eq(id))
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun activate(id: KUUID): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.ACTIVE, true)
      .set(AGENTS.UPDATED_AT, OffsetDateTime.now())
      .where(AGENTS.ID.eq(id))
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun deactivate(id: KUUID): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.ACTIVE, false)
      .set(AGENTS.UPDATED_AT, OffsetDateTime.now())
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
