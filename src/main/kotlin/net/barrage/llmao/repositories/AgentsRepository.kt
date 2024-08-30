package net.barrage.llmao.repositories

import net.barrage.llmao.models.Agent
import net.barrage.llmao.models.toAgent
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.references.*

class AgentsRepository {
    fun getAgents() : List<Agent> {
        return dslContext.selectFrom(AGENTS)
            .fetch()
            .map { it.toAgent() }
    }

    fun getAgent(id: Int) : Agent? {
        return dslContext.selectFrom(AGENTS)
            .where(AGENTS.ID.eq(id))
            .fetchOne()?.toAgent()
    }
}
