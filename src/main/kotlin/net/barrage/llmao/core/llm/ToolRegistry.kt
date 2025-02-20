package net.barrage.llmao.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.core.types.KUUID

typealias CallableTool = suspend (services: ServiceState, jsonArguments: String) -> String

const val GET_BIRTHDAY = "get_agent_birthday"

/** A registry for ALL tool definitions. Must be 1:1 with the tool implementations. */
object ToolRegistry {
  fun listToolDefinitions(): List<ToolDefinition> {
    return listOf(getAgentBirthdayDefinition())
  }

  fun getToolDefinition(name: String): ToolDefinition? {
    return when (name) {
      GET_BIRTHDAY -> getAgentBirthdayDefinition()
      else -> null
    }
  }

  fun getToolFunction(name: String): CallableTool? {
    return when (name) {
      GET_BIRTHDAY -> { services, arguments ->
          val toolData = Json.decodeFromString<GetAgentBirthdayArguments>(arguments)
          val agentId = toolData.agentId
          val birthday = services.agent.getAgent(agentId).createdAt
          "The birthday for agent $agentId is $birthday"
        }
      else -> null
    }
  }

  private fun getAgentBirthdayDefinition(): ToolDefinition {
    return ToolDefinition(
      type = "function",
      function =
        ToolFunctionDefinition(
          name = "get_agent_birthday",
          description =
            "Given an ID of an agent, returns the creation time, i.e. the birthday of the agent.",
          parameters =
            ToolFunctionParameters(
              type = "object",
              properties =
                mapOf(
                  "agent_id" to
                    ToolPropertyDefinition(
                      type = "string",
                      description = "The ID of the agent in UUID format.",
                    )
                ),
              required = listOf("agent_id"),
            ),
          strict = true,
        ),
    )
  }
}

@Serializable data class GetAgentBirthdayArguments(@SerialName("agent_id") val agentId: KUUID)
