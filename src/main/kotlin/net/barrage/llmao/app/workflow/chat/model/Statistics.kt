package net.barrage.llmao.app.workflow.chat.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.types.KUUID

@Serializable
data class AgentCounts(
  /** Total number of agents. */
  val total: Int,

  /** Number of active agents. */
  val active: Int,

  /** Number of inactive agents. */
  val inactive: Int,

  /** Number of agents per LLM provider. */
  val providers: Map<String, Int>,
)

/** The total number of chats and the number of chats for each agent. */
@Serializable data class ChatCounts(val total: Int, val chats: List<ChatCount>)

@Serializable data class ChatCount(val agentId: KUUID, val agentName: String, val count: Int)

@Serializable data class DashboardCounts(val chat: ChatCounts, val agent: AgentCounts)
