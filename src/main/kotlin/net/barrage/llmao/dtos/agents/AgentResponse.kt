package net.barrage.llmao.dtos.agents

import net.barrage.llmao.models.Agent

data class AgentResponse(
    val agents: List<Agent>,
    val count: Int
)
