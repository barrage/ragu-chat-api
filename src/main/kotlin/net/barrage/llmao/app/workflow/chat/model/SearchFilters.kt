package net.barrage.llmao.app.workflow.chat.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.app.http.QueryParameter
import net.barrage.llmao.tryUuid
import net.barrage.llmao.types.KUUID

data class SearchFiltersAdminAgents(val name: String?, val active: Boolean?)

data class SearchFiltersAdminChats(val userId: String?, val agentId: KUUID?, val title: String?)

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
