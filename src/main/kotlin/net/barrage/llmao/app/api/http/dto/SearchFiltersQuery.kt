package net.barrage.llmao.app.api.http.dto

import kotlinx.serialization.Serializable
import net.barrage.llmao.app.api.http.QueryParameter
import net.barrage.llmao.core.model.common.SearchFiltersAdminAgents
import net.barrage.llmao.core.model.common.SearchFiltersAdminChats
import net.barrage.llmao.tryUuid


@Serializable
data class SearchFiltersAdminAgentsQuery(
  @QueryParameter("name") var name: String?,
  @QueryParameter("active") var active: String?,
) {
  constructor() : this(null, null)

  fun toSearchFiltersAdminAgents(): SearchFiltersAdminAgents {
    return SearchFiltersAdminAgents(
      name = if (name.isNullOrBlank()) null else name,
      active = active?.toBoolean(),
    )
  }
}

@Serializable
data class SearchFiltersAdminChatQuery(
  @QueryParameter("userId") var userId: String?,
  @QueryParameter("agentId") var agentId: String?,
  @QueryParameter("title") var title: String?,
) {
  constructor() : this(null, null, null)

  fun toSearchFiltersAdminChats(): SearchFiltersAdminChats {
    return SearchFiltersAdminChats(
      userId = userId,
      agentId = agentId?.let { tryUuid(it) },
      title = if (title.isNullOrBlank()) null else title,
    )
  }
}
