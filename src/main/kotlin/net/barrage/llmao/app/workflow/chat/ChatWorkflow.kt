package net.barrage.llmao.app.workflow.chat

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolCallResult
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.llm.collectToolCalls
import net.barrage.llmao.core.models.MessageInsert
import net.barrage.llmao.core.tokens.TokenUsageTracker
import net.barrage.llmao.core.tokens.TokenUsageType
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.workflow.chat.ChatWorkflow")
private const val MANUAL_CANCEL = "manual_cancel"

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

  /** The current state of this workflow. */
  private var state: ChatWorkflowState,

  /**
   * If present, pops value from the front of the message history if the history gets larger than
   * this. TODO: replace with token based summarization.
   */
  private val maxHistory: Int = 20,

  /**
   * If present, summarize the chat using the history and swap the history with a single summary
   * message after the specified amount of tokens is reached.
   */
  private val summarizeAfterTokens: Int? = null,

  /** Chat message history. */
  private val history: MutableList<ChatMessage> = mutableListOf(),

  /** Used to track token usage. */
  private val tokenTracker: TokenUsageTracker,
) : Workflow {
  /** True if the chat is streaming, false otherwise. */
  private var stream: Job? = null

  /** The scope in which coroutines for this session are run. */
  private var streamScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

  override fun id(): KUUID {
    return id
  }

  override fun entityId(): KUUID {
    return agent.id
  }

  override fun execute(message: String) {
    if (stream != null) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    stream =
      streamScope.launch {
        val streamStart = Instant.now()

        // Copy the history so we can modify it without affecting the original
        // We only store the input and final output message.
        val input = history.toMutableList()
        input.add(ChatMessage.user(message))
        val response = StringBuilder()
        var finishReason = FinishReason.Stop

        try {
          stream(input, response)
        } catch (e: CancellationException) {
          // If the stream is cancelled from outside, via the session manager, a
          // CancellationException is thrown with a specific message indicating the reason.
          if (e.message != MANUAL_CANCEL) {
            LOG.error("Unexpected cancellation exception", e)
            handleError(e)
            return@launch
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
          streamScope.launch process@{
            if (response.isBlank()) {
              return@process
            }

            val messageId =
              processResponse(
                prompt = message,
                response = response.toString(),
                finishReason = finishReason,
              )

            LOG.debug("{} - emitting stream complete, finish reason: {}", id, finishReason.value)

            val emitPayload =
              ChatWorkflowMessage.StreamComplete(
                chatId = id,
                reason = finishReason,
                messageId = messageId,
              )

            emitter.emit(emitPayload)
          }

          stream = null
        }
      }
  }

  override fun cancelStream() {
    if (stream == null) {
      return
    }
    LOG.debug("{} - cancelling stream", id)
    stream!!.cancel(MANUAL_CANCEL)
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
  private suspend fun stream(
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

      if (chunk.tokenUsage != null) {
        assert(chunk.stopReason != null)
        tokenTracker.store(
          amount = chunk.tokenUsage,
          usageType = TokenUsageType.COMPLETION,
          model = agent.model,
          provider = agent.llmProvider,
        )
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

    stream(messages, out)
  }

  /** Persists messages depending on chat state. Returns the assistant message ID. */
  private suspend fun processResponse(
    prompt: String,
    response: String,
    finishReason: FinishReason,
  ): KUUID {
    val userMessage =
      MessageInsert(
        id = KUUID.randomUUID(),
        chatId = id,
        content = prompt,
        sender = userId,
        senderType = "user",
        responseTo = null,
        finishReason = null,
      )

    val assistantMessage =
      MessageInsert(
        id = KUUID.randomUUID(),
        chatId = id,
        content = response,
        sender = agent.configurationId,
        senderType = "assistant",
        responseTo = userMessage.id,
        finishReason = finishReason,
      )

    when (state) {
      ChatWorkflowState.New -> {
        LOG.debug("{} - persisting chat with message pair", id)
        repository.insertChat(
          chatId = id,
          userId = userId,
          agentId = agent.id,
          userMessage = userMessage,
          assistantMessage = assistantMessage,
        )

        LOG.debug("{} - generating title", id)
        val title = agent.createTitle(prompt, response)
        // Safe to !! because title generation never sends tools to the LLM
        repository.updateTitle(id, userId, title.content!!)
        emitter.emit(ChatWorkflowMessage.ChatTitleUpdated(id, title.content!!))
        state = ChatWorkflowState.Persisted(title.content!!)
      }
      is ChatWorkflowState.Persisted -> {
        LOG.debug("{} - persisting message pair", id)
        repository.insertMessagePair(userMessage = userMessage, assistantMessage = assistantMessage)
      }
    }

    addToHistory(listOf(ChatMessage.user(prompt), ChatMessage.assistant(response)))

    return assistantMessage.id
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
        // Safe to !! because summarization never sends any tools in the message, which means the
        // content will never be null
        repository.insertSystemMessage(id, summary.content!!)
        history.clear()
        history.add(ChatMessage.system(summary.content!!))
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
        emitter.emitError(e.withDisplayMessage(agent.instructions.errorMessage()))
      }
      else -> {
        LOG.error("Error in chat", e)
        emitter.emitError(AppError.internal().withDisplayMessage(agent.instructions.errorMessage()))
      }
    }
  }
}

sealed class ChatWorkflowState {
  /** State when a chat is created from scratch. It has no title and received no messages. */
  data object New : ChatWorkflowState()

  /**
   * State when a chat is persisted in the database. It has a title and received at least one
   * message.
   */
  data class Persisted(val title: String) : ChatWorkflowState()
}
