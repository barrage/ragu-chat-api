package net.barrage.llmao.core.models.common

import net.barrage.llmao.core.types.KUUID

data class SearchFiltersAdminUsers(val name: String?, val active: Boolean?, val role: Role?)

data class SearchFiltersAdminAgents(val name: String?, val active: Boolean?)

data class SearchFiltersAdminChats(val userId: String?, val agentId: KUUID?, val title: String?)
