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
  fun getAll(): List<Agent> {
    return dslContext.selectFrom(AGENTS).fetch(AgentsRecord::toAgent)
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
