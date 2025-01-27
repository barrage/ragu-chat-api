package net.barrage.llmao.core.llm

import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.core.repository.AgentRepository
import net.barrage.llmao.core.session.Emitter
import net.barrage.llmao.core.types.KUUID

internal val LOG =
  io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.llm.ToolchainFactory")

class ToolchainFactory(
  private val services: ServiceState,
  private val agentRepository: AgentRepository,
) {

  suspend fun createAgentToolchain(agentId: KUUID, emitter: Emitter<ToolEvent>? = null): Toolchain? {
    val agentTools = agentRepository.getAgentTools(agentId).map { it.toolName }

    if (agentTools.isEmpty()) {
      return null
    }

    if (emitter == null) {
      LOG.warn("Building toolchain without an emitter; Realtime tool call events will not be sent.")
    }

    val toolchain = ToolchainBuilder()

    for (tool in agentTools) {
      val definition = ToolRegistry.getToolDefinition(tool)
      if (definition == null) {
        LOG.warn("Attempted to load tool '$tool' but it does not exist in the tool registry")
        continue
      }

      val handler = ToolRegistry.getToolFunction(tool)
      if (handler == null) {
        LOG.warn("Attempted to load tool '$tool' but it does not have a handler")
        continue
      }

      toolchain.addTool(definition, handler)
    }

    LOG.info(
      "Loading toolchain for '{}', available tools: {}",
      agentId,
      toolchain.listToolNames().joinToString(", "),
    )

    return toolchain.build(services, emitter)
  }
}
