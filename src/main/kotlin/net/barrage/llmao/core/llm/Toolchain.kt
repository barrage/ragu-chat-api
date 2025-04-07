package net.barrage.llmao.core.llm

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.token.LOG
import net.barrage.llmao.core.workflow.Emitter

private val LOG = io.ktor.util.logging.KtorSimpleLogger("net.barrage.llmao.core.llm.Toolchain")

/**
 * Container for all available tools an agent can use. Holds all tool definitions and their handlers
 * obtained from the [ToolRegistry]. Holds an event listener to broadcast tool events, as well as
 * the service state for executing tool calls.
 *
 * Toolchains emit tool call events, 2 for each tool; one for the tool call and one for the tool
 * result.
 */
class Toolchain<S>(
  private val state: S,
  private val emitter: Emitter? = null,
  private val agentTools: List<ToolDefinition>,
  private val handlers: Map<String, CallableTool<S>>,
) {

  suspend fun processToolCall(
    data: ToolCallData,
    onError: suspend (ToolCallData, Throwable) -> ToolCallResult,
  ): ToolCallResult {
    LOG.debug("Tool call '{}' - start", data.name)

    emitter?.emit(ToolEvent.ToolCall(data), ToolEvent::class)

    if (data.id == null) {
      LOG.debug("Tool call '{}' - ID is null, result will not be correlated with call", data.name)
    }

    val handler = handlers[data.name]
    val result =
      try {
        handler?.invoke(state, data.arguments)
          ?: throw IllegalStateException("No handler for tool call '${data.name}'")
      } catch (e: Throwable) {
        val result = onError(data, e)
        emitter?.emit(ToolEvent.ToolResult(result), ToolEvent::class)
        return result
      }

    val toolResult = ToolCallResult(data.id, result)
    emitter?.emit(ToolEvent.ToolResult(toolResult), ToolEvent::class)

    return toolResult
  }

  fun listToolSchemas(): List<ToolDefinition> {
    return agentTools
  }
}

class ToolchainBuilder<S> {
  private val agentTools = mutableListOf<ToolDefinition>()
  private val handlers = mutableMapOf<String, CallableTool<S>>()

  fun addTool(definition: ToolDefinition, handler: CallableTool<S>): ToolchainBuilder<S> {
    agentTools.add(definition)
    handlers[definition.function.name] = handler
    return this
  }

  fun build(state: S, emitter: Emitter? = null) =
    Toolchain(emitter = emitter, state = state, agentTools = agentTools, handlers = handlers)

  fun listToolNames(): List<String> {
    return agentTools.map(ToolDefinition::function).map(ToolFunctionDefinition::name)
  }
}

/** Used to collect tool calls from streaming responses. */
fun collectToolCalls(toolCalls: MutableMap<Int, ToolCallData>, chunk: ChatMessageChunk) {
  val chunkToolCalls = chunk.toolCalls ?: return

  for (chunkToolCall in chunkToolCalls) {
    val index = chunkToolCall.index

    val toolCall = toolCalls[index]

    if (toolCall != null) {
      val callChunk = chunkToolCall.function?.arguments ?: ""
      toolCall.arguments += callChunk
      continue
    }
    // The tool name is sent in the first chunk
    // Since we don't have the tool in the map, it must be the first chunk for this
    // tool call.
    if (chunkToolCall.function?.name == null) {
      LOG.warn("Received tool call without function name")
      continue
    }

    toolCalls[index] =
      ToolCallData(
        id = chunkToolCall.id,
        name = chunkToolCall.function.name,
        arguments = chunkToolCall.function.arguments ?: "",
      )
  }
}

/**
 * Represents a finalized tool call. This can be obtained directly from LLM responses if not
 * streaming. If streaming, this is obtained by collecting the tool call chunks.
 */
@Serializable
data class ToolCallData(
  /** Tool correlation ID. Used by some LLMs to associate the tool result to the tool call. */
  val id: String? = null,

  /** The name of the tool as defined in the JSON schema. */
  val name: String,

  /**
   * The arguments for the tool call. It is generally a good idea to specify the arguments as a JSON
   * object so that it can be easily deserialized from the LLM.
   */
  var arguments: String,
)

/** Used to notify clients of tool calls and results. */
@Serializable
sealed class ToolEvent {
  /** Sent whenever an agent calls a tool. */
  @Serializable @SerialName("tool.call") data class ToolCall(val data: ToolCallData) : ToolEvent()

  /** Sent whenever an agent receives a tool result. */
  @Serializable
  @SerialName("tool.result")
  data class ToolResult(val result: ToolCallResult) : ToolEvent()
}

/** Used as a native application model for mapping tool call streams. */
@Serializable
data class ToolCallChunk(
  /** The index of the tool call, used to differentiate multiple tool calls. */
  val index: Int,
  /**
   * The tool call ID that needs to be sent back to the LLM to associate the tool response to the
   * call.
   */
  val id: String?,

  /**
   * Contains the function name and arguments.
   *
   * If streaming, the name is usually in the first tool call chunk.
   *
   * Argument chunks are then subsequently sent via `argumentsOrNull`. The arguments must be
   * collected into a JSON object.
   */
  val function: FunctionCall?,
)

/** Used as a native application model for mapping function call arguments. */
@Serializable data class FunctionCall(val name: String?, val arguments: String?)

/** Holds the result of a tool call and its correlation ID. */
@Serializable
data class ToolCallResult(
  /** Tool correlation ID. Used in most LLMs to associate the tool result to the tool call. */
  val id: String?,

  /** The result of the tool call. */
  val content: String,
)

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
  val properties: Map<String, ToolPropertyDefinition>,
  val required: List<String>,
  val additionalProperties: Boolean = false,
)

/** JSON schema property definition. */
@Serializable
data class ToolPropertyDefinition(
  val type: String,
  val description: String,
  val enum: List<String>? = null,
)
