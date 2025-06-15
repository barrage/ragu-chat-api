package net.barrage.llmao.app.workflow.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.barrage.llmao.app.workflow.chat.api.Api
import net.barrage.llmao.core.llm.CallableTool
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.ToolFunctionDefinition
import net.barrage.llmao.core.llm.ToolFunctionParameters
import net.barrage.llmao.core.llm.ToolPropertyDefinition
import net.barrage.llmao.core.types.KUUID

const val GET_BIRTHDAY = "get_agent_birthday"

/**
 * A registry for predefined tools that can be used by user-created agents.
 *
 * The registry contains the tool schemas and their implementations.
 */
object ChatToolExecutor {
  private lateinit var services: Api

  fun init(services: Api) {
    this.services = services
  }

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
      GET_BIRTHDAY -> ::getAgentBirthday
      else -> null
    }
  }

  private suspend fun getAgentBirthday(arguments: String): String {
    val toolData = Json.decodeFromString<GetAgentBirthdayArguments>(arguments)
    val agentId = toolData.agentId
    val birthday = services.admin.agent.getAgent(agentId).createdAt
    return "The birthday for agent $agentId is $birthday"
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
