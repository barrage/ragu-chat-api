package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

@Serializable
data class UpdateTools(
  /** List of tools to add to the agent. These tools must be defined in the global tool registry. */
  val add: List<String>,
  /** List of tools to remove from the agent. */
  val remove: List<String>,
)
