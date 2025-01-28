package net.barrage.llmao.core.llm

import kotlinx.serialization.Serializable
import net.barrage.llmao.app.ServiceState
import net.barrage.llmao.core.session.Emitter

/**
 * Container for all available tools an agent can use. Holds all tool definitions and their handlers
 * obtained from the [ToolRegistry]. Holds an event listener to broadcast tool events, as well as
 * the service state for executing tool calls.
 *
 * Toolchains emit tool call events, 2 for each tool; one for the tool call and one for the tool
 * result.
 */
class Toolchain(
  private val emitter: Emitter<ToolEvent>? = null,
  private val services: ServiceState,
  private val agentTools: List<ToolDefinition>,
  private val handlers: Map<String, CallableTool>,
) {

  suspend fun processToolCall(data: ToolCallData): ToolCallResult {
    emitter?.emit(ToolEvent.ToolCall(data))

    // TODO: Use already complete input for tool calling, ToolCallData represents a chunk
    val handler = handlers[data.function?.name]
    val result =
      handler?.invoke(services, data)
        ?: throw IllegalStateException("No handler for tool call '${data.function!!.name}'")

    emitter?.emit(ToolEvent.ToolResult(result))

    return result
  }

  fun listToolSchemas(): List<ToolDefinition> {
    return agentTools
  }
}

class ToolchainBuilder {
  private val agentTools = mutableListOf<ToolDefinition>()
  private val handlers = mutableMapOf<String, CallableTool>()

  fun addTool(definition: ToolDefinition, handler: CallableTool) {
    agentTools.add(definition)
    handlers[definition.function.name] = handler
  }

  fun build(services: ServiceState, emitter: Emitter<ToolEvent>? = null) =
    Toolchain(emitter = emitter, services = services, agentTools = agentTools, handlers = handlers)

  fun listToolNames(): List<String> {
    return agentTools.map(ToolDefinition::function).map(ToolFunctionDefinition::name)
  }
}

/** Used to notify clients of tool calls and results. */
sealed class ToolEvent {
  /** Sent whenever an agent calls a tool. */
  data class ToolCall(val data: ToolCallData) : ToolEvent()

  /** Sent whenever an agent receives a tool result. */
  data class ToolResult(val result: ToolCallResult) : ToolEvent()
}

/** Used as a native application model for mapping tool call streams. */
@Serializable data class ToolCallData(val id: String?, val function: FunctionCall?)

/** Used as a native application model for mapping function call arguments. */
@Serializable data class FunctionCall(val name: String?, val arguments: String?)

@Serializable data class ToolCallResult(val id: String, val result: String)

@Serializable data class ToolDefinition(val type: String, val function: ToolFunctionDefinition)

@Serializable
data class ToolFunctionDefinition(
  val name: String,
  val description: String,
  val parameters: ToolFunctionParameters,
  val strict: Boolean,
)

@Serializable
data class ToolFunctionParameters(
  val type: String,
  val properties: Map<String, ToolFunctionPropertyDefinition>,
  val required: List<String>,
  val additionalProperties: Boolean = false,
)

@Serializable
data class ToolFunctionPropertyDefinition(
  val type: String,
  val description: String,
  val enum: List<String>? = null,
)
