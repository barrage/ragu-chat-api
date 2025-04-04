package net.barrage.llmao.core.workflow

import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.chat.ChatHistory
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.ContextEnrichment
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.LlmProvider
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolDefinition
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.llm.collectToolCalls
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType

private const val DEFAULT_MAX_TOOL_ATTEMPTS: Int = 5

abstract class WorkflowAgent<S, E>(

  /**
   * User entitlements are used to check application permissions.
   *
   * In cases of RAG when the `groups` properties in collections is present, they will be checked to
   * see if the agent can access that collection.
   *
   * If the user is in a group from the groups property, the agent can access that collection.
   */
  val user: User,

  /** The LLM. */
  val model: String,

  /** LLM provider. */
  val llm: LlmProvider,

  /** The system message. Not included in histories, always sent to the LLM. */
  protected val context: String,

  /** Completion parameters used for inference. */
  protected val completionParameters: ChatCompletionParameters,

  /** LLM tools. */
  protected val toolchain: Toolchain<S>?,

  /** Used to track token usage, if applicable. */
  protected val tokenTracker: TokenUsageTracker,

  /**
   * The chat history. Has to be managed from outside since messages need to be stored, and we can
   * only reason about which messages to add based on whether or not they are successfully stored.
   */
  protected val history: ChatHistory,

  /**
   * Output handle. Used in cases where responses need to be emitted to the client in real time,
   * e.g. websockets.
   */
  protected val emitter: Emitter<E>? = null,

  /** Prevents the LLM from infinitely calling tools. */
  protected val maxToolAttempts: Int = DEFAULT_MAX_TOOL_ATTEMPTS,

  /** Used to convert attachments on input messages. */
  protected val attachmentProcessor: ChatMessageProcessor,

  /**
   * Used to enrich the agent's context. The semantics of what this means is up to the
   * implementation.
   */
  protected val contextEnrichment: List<ContextEnrichment>?,
) {
  /** Available tools. */
  val tools: List<ToolDefinition>? = toolchain?.listToolSchemas()

  protected val log = KtorSimpleLogger("net.barrage.llmao.core.workflow.WorkflowAgent")

  /**
   * Callback that executes when streaming content is received from the LLM.
   *
   * This will be called for each chunk that contains content.
   *
   * Executes only on [stream].
   */
  abstract suspend fun onContentChunk(content: String)

  /**
   * Callback that executes when a complete response is received from the LLM.
   *
   * Executes only on [completion].
   */
  abstract suspend fun onContent(content: String)

  /** Used to identify the agent. */
  abstract fun id(): String

  /**
   * Recursively calls the chat completion stream until no tool calls are returned.
   *
   * If the agent contains no tools, the response content will be streamed on the first call and
   * this function will be called only once. Note that each call to the LLM is done in streaming
   * mode.
   *
   * If the agent contains tools and decides to use them, it will usually stream only the tool calls
   * as the initial response. The tools must then be called and their results sent back to the LLM.
   * This process is repeated until the LLM outputs a final text response or the maximum number of
   * tool attempts is reached.
   *
   * The final output of the LLM will be captured in `out`.
   *
   * @param userMessage Immutable reference to the original user message.
   * @param messageBuffer A buffer that keeps track of new messages that are not yet persisted. Gets
   *   populated with all the messages that occurred during inference including the final assistant
   *   response.
   * @param out A buffer to capture the final response of the LLM. If the stream gets cancelled and
   *   this is not empty, it means a manual cancel occurred. If this is empty at the end of the
   *   stream it means the stream fully completed and the assistant message is the last message in
   *   the buffer.
   * @throws AppError If the emitter is null.
   */
  suspend fun stream(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    out: StringBuilder,
  ) {
    if (emitter == null) {
      throw AppError.internal("Workflow agent attempted to stream without an emitter")
    }
    streamInner(userMessage, messageBuffer, out, 0)
  }

  /**
   * See [stream].
   *
   * @param attempt Current attempt. Used to prevent infinite loops.
   */
  private suspend fun streamInner(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    out: StringBuilder,
    attempt: Int = 0,
  ) {
    // Perform RAG only on the first attempt and swap the initial user message with an enriched one.
    // We need to copy the original since we do not store the enriched message, only the original.
    val userMessage =
      if (attempt == 0) {
        assert(messageBuffer.isEmpty())
        var content = userMessage.content!!.text()
        contextEnrichment?.let {
          for (enrichment in it) {
            content = enrichment.enrich(content)
          }
        }
        userMessage.copy(content = userMessage.content!!.copyWithText(content))
      } else userMessage

    log.debug("{} - starting stream (attempt: {})", id(), attempt + 1)

    // I seriously don't understand why this is needed but it is
    // the only way I've found to not alter the original messages
    // this can't be good I just don't get it
    val llmInput =
      mutableListOf<ChatMessage>(ChatMessage.system(context)) +
        history +
        userMessage +
        messageBuffer

    val llmStream =
      llm.completionStream(
        llmInput,
        completionParameters.copy(tools = if (attempt < maxToolAttempts) tools else null),
      )

    val toolCalls: MutableMap<Int, ToolCallData> = mutableMapOf()

    // The finish reason will get adjusted when the last chunk found.
    // It is guaranteed that the LLM stream will return the finish reason.
    var finishReason: FinishReason? = null

    llmStream.collect { chunk ->
      // Chunks with some content indicate this is a text chunk
      // and should be emitted.
      if (!chunk.content.isNullOrEmpty()) {
        onContentChunk(chunk.content)
        out.append(chunk.content)
      }

      chunk.stopReason?.let { reason -> finishReason = reason }

      chunk.tokenUsage?.let { tokenUsage ->
        tokenTracker.store(
          amount = tokenUsage,
          usageType = TokenUsageType.COMPLETION,
          model = model,
          provider = llm.id(),
        )
      }

      collectToolCalls(toolCalls, chunk)
    }

    // If the coroutine gets cancelled here, we are certain `out` will be cleared and the
    // last message is the assistant's.

    // I may have seen some messages that have both tool calls and content
    // but I cannot confirm nor deny this
    assert(out.isNotEmpty() && toolCalls.isEmpty() || toolCalls.isNotEmpty() && out.isBlank())

    messageBuffer.add(
      ChatMessage.assistant(
        content = if (out.isEmpty()) null else out.toString(),
        toolCalls = if (toolCalls.isEmpty()) null else toolCalls.values.toList(),
        // If the stream gets cancelled during collecting, the method is already cancelled
        // and we never reach this point, so safe to !!
        finishReason = finishReason!!,
      )
    )
    out.clear()

    if (toolCalls.isEmpty()) {
      return
    }

    // From this point on, we are handling tool calls and we need to re-prompt the LLM with
    // their results.

    log.debug(
      "{} - (stream) calling tools: {}",
      id(),
      toolCalls.values.joinToString(", ") { it.name },
    )

    for (toolCall in toolCalls.values) {
      // Safe to !! because toolchain is not null if toolCalls is not empty
      try {
        val result = toolchain!!.processToolCall(toolCall)
        messageBuffer.add(ChatMessage.toolResult(result.content, toolCall.id))
      } catch (e: Throwable) {
        messageBuffer.add(ChatMessage.toolResult("error: ${e.message}", toolCall.id))
      }
    }

    streamInner(
      userMessage = userMessage,
      messageBuffer = messageBuffer,
      out = out,
      attempt = attempt + 1,
    )
  }

  fun addToHistory(messages: List<ChatMessage>) {
    history.add(messages)
  }

  /**
   * Execute chat completion without streaming.
   *
   * @return A list of messages that occurred during inference. If no tools are called, this will
   *   contain the user and assistant message pair. If the agent calls tools, all calls and results
   *   will be captured in between.
   */
  suspend fun completion(
    text: String,
    attachments: List<IncomingMessageAttachment>? = null,
  ): List<ChatMessage> {
    var text = text
    contextEnrichment?.let {
      for (enrichment in it) {
        text = enrichment.enrich(text)
      }
    }

    val content =
      attachments?.let { attachmentProcessor.toContentMulti(text, it) } ?: ContentSingle(text)

    val messageBuffer = mutableListOf<ChatMessage>()

    completion(ChatMessage.user(content), messageBuffer)

    // Original content for storage, without any enrichment
    val originalUserMessage =
      attachments?.let { attachmentProcessor.toContentMulti(text, it) } ?: ContentSingle(text)

    return listOf(ChatMessage.user(originalUserMessage)) + messageBuffer
  }

  /**
   * Inner implementation that runs recursively until no tool calls are returned from the LLM or the
   * maximum attempts are reached.
   *
   * Populates [messageBuffer] with all the messages that occurred during inference.
   *
   * @param userMessage The user message. Passed solely as an immutable reference.
   * @param messageBuffer The message buffer for tracking messages. As this gets called, this will
   *   get populated with all the messages that occurred during inference, including the final
   *   assistant message.
   */
  private suspend fun completion(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    attempt: Int = 0,
  ) {
    log.debug("{} - starting completion (attempt: {})", id(), attempt + 1)

    val llmInput = listOf(ChatMessage.system(context)) + history + userMessage + messageBuffer

    val completion =
      llm.chatCompletion(
        messages = llmInput,
        config =
          completionParameters.copy(
            tools = if (attempt < DEFAULT_MAX_TOOL_ATTEMPTS) tools else null
          ),
      )

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION,
        model = model,
        provider = llm.id(),
      )
    }

    val message = completion.choices.first().message

    messageBuffer.add(message)

    message.content?.let { content -> onContent(content.text()) }

    if (message.toolCalls != null) {
      log.debug(
        "{} - (completion) calling tools: {}",
        id(),
        message.toolCalls.joinToString(", ") { it.name },
      )

      for (toolCall in message.toolCalls) {
        val result = toolchain!!.processToolCall(toolCall)
        messageBuffer.add(ChatMessage.toolResult(result.content, toolCall.id))
      }

      completion(userMessage, messageBuffer, attempt + 1)
    } else if (message.content == null) {
      log.warn("{} - assistant message has no content and no tools have been called", id())
    }
  }
}
