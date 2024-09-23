package net.barrage.llmao.repositories

import java.time.OffsetDateTime
import net.barrage.llmao.dtos.agents.NewAgentDTO
import net.barrage.llmao.dtos.agents.UpdateAgentDTO
import net.barrage.llmao.models.Agent
import net.barrage.llmao.models.toAgent
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.records.AgentsRecord
import net.barrage.llmao.tables.references.AGENTS

class AgentRepository {
  fun getAll(
    offset: Int,
    size: Int,
    sortBy: String,
    sortOrder: String,
    showDeactivated: Boolean,
  ): List<Agent> {
    val sortField =
      when (sortBy) {
        "name" -> AGENTS.NAME
        "createdAt" -> AGENTS.CREATED_AT
        else -> AGENTS.CREATED_AT
      }

    val orderField =
      if (sortOrder.equals("desc", ignoreCase = true)) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return dslContext
      .selectFrom(AGENTS)
      .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else null)
      .orderBy(orderField)
      .limit(size)
      .offset(offset)
      .fetch(AgentsRecord::toAgent)
  }

  fun countAll(showDeactivated: Boolean): Int {
    return dslContext
      .selectCount()
      .from(AGENTS)
      .where(if (!showDeactivated) AGENTS.ACTIVE.eq(true) else null)
      .fetchOne(0, Int::class.java)!!
  }

  fun get(id: Int): Agent? {
    return dslContext.selectFrom(AGENTS).where(AGENTS.ID.eq(id)).fetchOne(AgentsRecord::toAgent)
  }

  fun create(newAgent: NewAgentDTO): Agent? {
    return dslContext
      .insertInto(AGENTS)
      .set(AGENTS.NAME, newAgent.name)
      .set(AGENTS.CONTEXT, newAgent.context)
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun update(id: Int, updated: UpdateAgentDTO): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.NAME, updated.name)
      .set(AGENTS.CONTEXT, updated.context)
      .set(AGENTS.UPDATED_AT, OffsetDateTime.now())
      .where(AGENTS.ID.eq(id))
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun activate(id: Int): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.ACTIVE, true)
      .set(AGENTS.UPDATED_AT, OffsetDateTime.now())
      .where(AGENTS.ID.eq(id))
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }

  fun deactivate(id: Int): Agent? {
    return dslContext
      .update(AGENTS)
      .set(AGENTS.ACTIVE, false)
      .set(AGENTS.UPDATED_AT, OffsetDateTime.now())
      .where(AGENTS.ID.eq(id))
      .returning()
      .fetchOne(AgentsRecord::toAgent)
  }
}
