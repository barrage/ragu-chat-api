package net.barrage.llmao.core.workflow.chat

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolCallResult
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.error.AppError

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.workflow.ChatWorkflow")

/** Implementation of a workflow with a single agent. */
class ChatWorkflow(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val userId: KUUID,

  /** LLM tools. */
  private val toolchain: Toolchain? = null,

  /** Output handle. Only chat related events are sent via this reference. */
  private val emitter: Emitter<ChatWorkflowMessage>,

  /** Encapsulates the agent and its LLM functionality. */
  private val agent: ChatAgent,

  /** Responsible for persisting chat data. */
  private val repository: ChatWorkflowRepository,

  /** Used to reason about storing the chat. */
  private var messageReceived: Boolean = false,

  /** Generated after the first message is received in this chat. */
  private var title: String? = null,

  /**
   * If present, pops value from the front of the message history if the history gets larger than
   * this.
   */
  private val maxHistory: Int = 20,

  /**
   * If present, summarize the chat using the history and swap the history with a single summary
   * message after the specified amount of tokens is reached.
   */
  private val summarizeAfterTokens: Int? = null,

  /** Chat message history. */
  private val history: MutableList<ChatMessage> = mutableListOf(),
) : Workflow {
  /** True if the chat is streaming, false otherwise. */
  private var stream: Job? = null

  /** The scope in which coroutines for this session are run. */
  private var streamScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

  override fun id(): KUUID {
    return id
  }

  override fun entityId(): KUUID {
    return agent.agent.id
  }

  override fun send(message: String) {
    stream =
      streamScope.launch {
        val streamStart = Instant.now()

        val input = history.toMutableList()
        input.add(ChatMessage.user(message))
        val response = StringBuilder()
        var finishReason = FinishReason.Stop

        println("HISTORY PRE: $history")
        try {
          // Copy the history so we can modify it without affecting the original
          executeStreamRecursive(input, response)
        } catch (e: CancellationException) {
          // If the stream is cancelled from outside, via the session manager, a
          // CancellationException is thrown with a specific message indicating the reason.
          if (e.message != "manual_cancel") {
            LOG.error("Unexpected cancellation exception", e)
          }

          finishReason = FinishReason.ManualStop

          LOG.debug(
            "{} - stream cancelled, took {}ms, storing response: {}",
            id,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
            response.isNotBlank(),
          )
        } catch (e: Throwable) {
          LOG.error("Unexpected error in stream", e)
          handleError(e)
        } finally {
          streamScope.launch {
            if (response.isNotBlank()) {
              processResponse(prompt = message, response = response.toString())
            }

            println("HISTORY POST: $history")

            LOG.debug("{} - emitting stream complete, finish reason: {}", id, finishReason.value)
            val emitPayload = ChatWorkflowMessage.StreamComplete(id, reason = finishReason)
            emitter.emit(emitPayload)
          }

          stream = null
        }
      }
  }

  override fun isStreaming(): Boolean {
    return stream != null
  }

  override fun cancelStream() {
    if (stream == null) {
      return
    }
    LOG.debug("{} - cancelling stream", id)
    stream!!.cancel("manual_cancel")
  }

  /**
   * Recursively calls the chat completion stream until no tool calls are returned.
   *
   * If the agent contains no tools, the response content will be streamed on the first call and
   * this function will be called only once.
   *
   * If the agent contains tools and decides to use them, it will usually stream only the tool calls
   * as the initial response. The tools must then be called and their results sent back to the LLM.
   * This process is repeated until the LLM outputs a final text response.
   *
   * The final output of the LLM will be captured in `out`.
   *
   * @param messages Initially call with the chat history, then if necessary with the tool results.
   *   The tool results will get appended to the history.
   * @param out A buffer to capture the final response of the LLM.
   * @param attempts Number of attempts to call the LLM with tools. Used to prevent infinite loops.
   */
  private suspend fun executeStreamRecursive(
    messages: MutableList<ChatMessage>,
    out: StringBuilder,
    attempts: Int = 0,
  ) {
    LOG.debug("{} - starting stream (attempt: {})", id, attempts + 1)

    val llmStream =
      try {
        agent.chatCompletionStream(
          messages,
          useRag = true,
          // Stop sending tools if we are "stuck" in a loop
          useTools = attempts < 5 && toolchain != null,
        )
      } catch (e: AppError) {
        handleError(e)
        return
      }

    val toolCalls: MutableMap<Int, ToolCallData> = mutableMapOf()

    llmStream.collect { chunk ->
      // Chunks with some content indicate this is a text chunk
      if (!chunk.content.isNullOrEmpty()) {
        emitter.emit(ChatWorkflowMessage.StreamChunk(chunk.content))
        out.append(chunk.content)
      }

      collectToolCalls(toolCalls, chunk)
    }

    assert(out.isNotBlank() && toolCalls.isEmpty() || toolCalls.isNotEmpty() && out.isBlank())

    if (toolCalls.isEmpty() && out.isNotBlank()) {
      return
    }

    // From this point on, we are handling tool calls and we need to re-prompt the LLM with
    // their results.

    messages.add(
      ChatMessage.assistant(if (out.isEmpty()) null else out.toString(), toolCalls.values.toList())
    )

    LOG.debug("{} - calling tools: {}", id, toolCalls.values.joinToString(", ") { it.name })

    val correlatedToolResults = mutableMapOf<String, ToolCallResult>()

    // TODO: figure out what to do with this, probably just append to original prompt and re-prompt
    val uncorrelatedToolResults = mutableListOf<ToolCallResult>()

    // Correlate any tool calls that have an ID
    for (toolCall in toolCalls.values) {
      // Safe to !! because toolchain is not null if toolCalls is not empty
      val result = toolchain!!.processToolCall(toolCall)
      if (result.id != null) {
        correlatedToolResults[result.id] = result
      } else {
        uncorrelatedToolResults.add(result)
      }
    }

    for (result in correlatedToolResults.values) {
      messages.add(ChatMessage.toolResult(result.content, result.id))
    }

    executeStreamRecursive(messages, out)
  }

  private suspend fun processResponse(prompt: String, response: String) {
    if (!messageReceived) {
      LOG.debug("{} - persisting chat with message pair", id)
      messageReceived = true
      repository.insertChat(id, userId, prompt, agent.agent.id, response)
    } else {
      LOG.debug("{} - persisting message pair", id)
      repository.insertMessagePair(
        chatId = id,
        userId = userId,
        prompt = prompt,
        agentConfigurationId = agent.agent.configurationId,
        response,
      )
    }

    if (title.isNullOrBlank()) {
      LOG.debug("{} - generating title", id)
      title = agent.createTitle(prompt, response)
      repository.updateTitle(id, userId, title!!)
      emitter.emit(ChatWorkflowMessage.ChatTitleUpdated(id, title!!))
    }

    addToHistory(listOf(ChatMessage.user(prompt), ChatMessage.assistant(response)))
  }

  private fun collectToolCalls(toolCalls: MutableMap<Int, ToolCallData>, chunk: ChatMessageChunk) {
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
        LOG.warn("{} - Received tool call without function name", id)
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
   * Adds the messages to the history and summarizes the conversation if necessary.
   *
   * If the history exceeds the maximum size, the oldest message is removed.
   *
   * TODO: See what to do with tool messages and how to handle their removal.
   */
  private suspend fun addToHistory(messages: List<ChatMessage>) {
    history.addAll(messages)

    summarizeAfterTokens?.let {
      val tokenCount = agent.countHistoryTokens(history)
      if (tokenCount >= it) {
        val summary = agent.summarizeConversation(history)
        repository.insertSystemMessage(id, summary)
        history.clear()
        history.add(ChatMessage.system(summary))
      }
      return@addToHistory
    }

    if (history.size > maxHistory) {
      history.removeFirst()
    }
  }

  /**
   * Stops streaming and emits an error message.
   *
   * An API error will be sent if it is an application error.
   *
   * Internal otherwise.
   */
  private suspend fun handleError(e: Throwable) {
    when (e) {
      is AppError -> {
        emitter.emitError(e)
      }
      else -> {
        LOG.error("Error in chat", e)
        emitter.emitError(AppError.internal())
      }
    }
  }
}
