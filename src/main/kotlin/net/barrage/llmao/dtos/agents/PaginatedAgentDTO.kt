package net.barrage.llmao.dtos.agents

import kotlinx.serialization.Serializable
import net.barrage.llmao.dtos.PaginationInfo
import net.barrage.llmao.models.Agent

@Serializable
data class PaginatedAgentDTO (
    val agents: List<Agent>,
    val pageInfo: PaginationInfo
)

fun toPaginatedAgentDTO(agents: List<Agent>, pageInfo: PaginationInfo): PaginatedAgentDTO {
    return PaginatedAgentDTO(
        agents = agents,
        pageInfo = pageInfo
    )
}
