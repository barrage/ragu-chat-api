package net.barrage.llmao.core.llm

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.EventListener

/** Used for handling tool calls in an active chat session. */
class LlmToolchain(
  private val listener: EventListener<ToolEvent>,
  private val agentTools: List<ToolDefinition>,
  private val handler: suspend (ToolCallData) -> ToolCallResult,
) {

  suspend fun processToolCall(data: ToolCallData): ToolCallResult {
    listener.dispatch(ToolEvent.ToolCall(data))

    val result = handler(data)

    listener.dispatch(ToolEvent.ToolResult(result))

    return result
  }

  fun listToolSchemas(): List<ToolDefinition> {
    return agentTools
  }
}

sealed class ToolEvent {
  data class ToolCall(val data: ToolCallData) : ToolEvent()

  data class ToolResult(val result: ToolCallResult) : ToolEvent()
}

@Serializable data class ToolCallData(val id: String, val type: String, val function: FunctionCall)

@Serializable data class FunctionCall(val name: String, val arguments: String)

@Serializable data class ToolCallResult(val id: String, val type: String, val result: String)

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
