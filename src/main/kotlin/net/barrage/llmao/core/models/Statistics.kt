package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

@Serializable data class GraphData(val name: String, val value: Int)

@Serializable data class LineChartKeys(val name: String, val data: List<GraphData>)

@Serializable
data class UserCounts(
  val total: Int,
  val active: Int,
  val inactive: Int,
  val admin: Int,
  val user: Int,
)

@Serializable
data class AgentCounts(
  val total: Int,
  val active: Int,
  val inactive: Int,
  val providers: List<GraphData>,
)

@Serializable data class ChatCounts(val total: Int, val agents: List<GraphData>)

@Serializable
data class DashboardCounts(val chat: ChatCounts, val agent: AgentCounts, val user: UserCounts)
