package net.barrage.llmao.app.workflow.chat.api

import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentFull
import net.barrage.llmao.app.workflow.chat.repository.AgentRepository
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.ProviderState
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.types.KUUID

/** End-user agent API. */
class PublicAgentService(
  private val providers: ProviderState,
  private val repository: AgentRepository,
) {
  /** List all agents visible to the user (based on their groups). */
  suspend fun listAgents(
    pagination: PaginationSort,
    showDeactivated: Boolean,
    groups: List<String>,
  ): CountedList<Agent> =
    repository.getAll(pagination, showDeactivated, groups, providers.listAvailableLlms())

  suspend fun getAgent(id: KUUID, groups: List<String>): Agent =
    repository.getAgent(id, groups)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")

  suspend fun getFull(id: KUUID, groups: List<String>): AgentFull =
    repository.userGet(id, groups)
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Agent not found")
}
