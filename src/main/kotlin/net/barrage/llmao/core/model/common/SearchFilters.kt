package net.barrage.llmao.core.model.common

import net.barrage.llmao.types.KUUID

data class SearchFiltersAdminAgents(val name: String?, val active: Boolean?)

data class SearchFiltersAdminChats(val userId: String?, val agentId: KUUID?, val title: String?)
