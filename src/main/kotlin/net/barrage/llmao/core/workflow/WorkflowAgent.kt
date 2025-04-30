package net.barrage.llmao.core.workflow

import io.ktor.util.logging.KtorSimpleLogger
import net.barrage.llmao.core.chat.ChatHistory
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatCompletionAgentParameters
import net.barrage.llmao.core.llm.ChatCompletionBaseParameters
import net.barrage.llmao.core.llm.ChatCompletionParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.ContextEnrichment
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.InferenceProvider
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.Tools
import net.barrage.llmao.core.llm.collectToolCalls
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.token.TokenUsageTracker
import net.barrage.llmao.core.token.TokenUsageType

private const val DEFAULT_MAX_TOOL_ATTEMPTS: Int = 5

typealias StreamHandler = suspend (ChatMessageChunk) -> Unit

abstract class WorkflowAgent(
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
  val inferenceProvider: InferenceProvider,

  /** Completion parameters used for inference. */
  protected val completionParameters: ChatCompletionBaseParameters,

  /** Used to track token usage, if applicable. */
  protected val tokenTracker: TokenUsageTracker,

  /**
   * The chat history. Has to be managed from outside since messages need to be stored, and we can
   * only reason about which messages to add based on whether or not they are successfully stored.
   *
   * If this is null, the agent will not use history.
   */
  protected val history: ChatHistory?,

  /** Prevents the LLM from infinitely calling tools. */
  protected val maxToolAttempts: Int = DEFAULT_MAX_TOOL_ATTEMPTS,

  /**
   * Used to enrich the agent's context. The semantics of what this means is up to the
   * implementation.
   */
  protected val contextEnrichment: List<ContextEnrichment>?,
) {

  protected open val log = KtorSimpleLogger("n.b.l.c.workflow.WorkflowAgent")

  /** Used to identify the agent. */
  abstract fun id(): String

  /** The system message. Not included in histories, always sent to the LLM. */
  abstract fun context(): String

  open fun errorMessage(): String = "An error occurred. Please try again later."

  /**
   * Recursively calls the chat completion stream until no tool calls or tool results are returned.
   *
   * If the agent contains no tools, the response content will be streamed on the first call and
   * this function will be called only once. Note that each call to the LLM is done in streaming
   * mode.
   *
   * If the agent calls tools, they will be called recursively until the final response is obtained.
   *
   * The final output of the LLM will be captured in `out`.
   *
   * @param userMessage Immutable reference to the original user message.
   * @param messageBuffer A buffer that keeps track of new messages that are not yet persisted. Gets
   *   populated with all the messages that occurred during inference including the final assistant
   *   response.
   * @param out A buffer to capture the final response of the LLM. If the stream gets cancelled and
   *   this is not empty, it means a manual cancel occurred. If this is empty at the end of the
   *   stream it means either the stream fully completed and the assistant response is the last
   *   message in the buffer or the assistant's final response never begun.
   * @param streamHandler Callback that executes on each chunk received. Useful for streaming
   *   responses.
   */
  suspend fun stream(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    out: StringBuilder,
    streamHandler: StreamHandler,
    params: ChatCompletionAgentParameters? = null,
    emitter: Emitter? = null,
  ) = streamInner(userMessage, messageBuffer, out, streamHandler, params, emitter, 0)

  /**
   * See [stream].
   *
   * @param attempt Current attempt. Used to prevent infinite loops.
   */
  private suspend fun streamInner(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    out: StringBuilder,
    streamHandler: StreamHandler,
    params: ChatCompletionAgentParameters? = null,
    emitter: Emitter? = null,
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

    val history = history ?: emptyList()
    val llmInput =
      listOf<ChatMessage>(ChatMessage.system(context())) + history + userMessage + messageBuffer

    val llmStream =
      inferenceProvider.completionStream(
        llmInput,
        ChatCompletionParameters(
          base = completionParameters,
          agent = params?.copy(tools = if (attempt < maxToolAttempts) params.tools else null),
        ),
      )

    val toolCalls: MutableMap<Int, ToolCallData> = mutableMapOf()

    // The finish reason will get adjusted when the last chunk is found.
    // It is guaranteed that the LLM stream will return the finish reason.
    var finishReason: FinishReason? = null

    llmStream.collect { chunk ->
      streamHandler(chunk)

      if (!chunk.content.isNullOrEmpty()) {
        out.append(chunk.content)
      }

      chunk.stopReason?.let { reason -> finishReason = reason }

      chunk.tokenUsage?.let { tokenUsage ->
        tokenTracker.store(
          amount = tokenUsage,
          usageType = TokenUsageType.COMPLETION,
          model = model,
          provider = inferenceProvider.id(),
        )
      }

      collectToolCalls(toolCalls, chunk)
    }

    // If the coroutine gets cancelled here, we are certain `out` will be cleared and the
    // last message is the assistant's.

    // I may have seen some messages that have both tool calls and content
    // but I can neither confirm nor deny this
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
      // No tool calls means inference is finished
      return
    }

    handleToolCalls(toolCalls.values.toList(), params!!.tools!!, messageBuffer, emitter)

    streamInner(
      userMessage = userMessage,
      messageBuffer = messageBuffer,
      out = out,
      attempt = attempt + 1,
      streamHandler = streamHandler,
      params = params,
    )
  }

  /** Add messages to the agent's history. */
  fun addToHistory(messages: List<ChatMessage>) {
    history?.add(messages)
  }

  /**
   * Executes chat completion without streaming and collects the whole interaction.
   *
   * **This implementation includes the user message in the returned list.**
   *
   * This implementation is intended to be used outside of real-time workflows. By default, it does
   * not handle tool calls.
   *
   * @return A list of messages that occurred during inference. If no tools are called, this will
   *   contain the user and assistant message pair. If the agent calls tools, all calls and results
   *   will be captured in between.
   */
  suspend fun completion(
    text: String,
    attachments: List<IncomingMessageAttachment>? = null,
    params: ChatCompletionAgentParameters? = null,
    emitter: Emitter? = null,
  ): List<ChatMessage> {
    var text = text
    contextEnrichment?.let {
      for (enrichment in it) {
        text = enrichment.enrich(text)
      }
    }

    val content =
      attachments?.let { ChatMessageProcessor.toContentMulti(text, it) } ?: ContentSingle(text)

    val messageBuffer = mutableListOf<ChatMessage>()

    completionInner(
      userMessage = ChatMessage.user(content),
      messageBuffer = messageBuffer,
      params = params,
      emitter = emitter,
    )

    // Original content for storage, without any enrichment
    val originalUserMessage =
      attachments?.let { ChatMessageProcessor.toContentMulti(text, it) } ?: ContentSingle(text)

    return listOf(ChatMessage.user(originalUserMessage)) + messageBuffer
  }

  /**
   * Executes chat completion without streaming and collects the interaction to the passed in
   * message buffer.
   *
   * **This implementation does not include the user message in the buffer when the invocation
   * ends.**
   *
   * This implementation should be used in cases where the completion is cancellable.
   *
   * @param userMessage Immutable reference to the original user message.
   * @param messageBuffer The message buffer for tracking messages.
   * @param params Additional parameters for tools and response formats.
   * @param emitter Emitter for realtime events. In the case of this method, tool calls and results
   *   will be forwarded.
   */
  suspend fun completion(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    params: ChatCompletionAgentParameters? = null,
    emitter: Emitter? = null,
  ) {
    var text = userMessage.content!!.text()

    contextEnrichment?.let {
      for (enrichment in it) {
        text = enrichment.enrich(text)
      }
    }

    val message = userMessage.copy(content = userMessage.content!!.copyWithText(text))

    completionInner(message, messageBuffer, params, emitter, 0)
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
  private suspend fun completionInner(
    userMessage: ChatMessage,
    messageBuffer: MutableList<ChatMessage>,
    params: ChatCompletionAgentParameters? = null,
    emitter: Emitter? = null,
    attempt: Int = 0,
  ) {
    if (attempt == 0) {
      assert(messageBuffer.isEmpty())
    }

    log.debug("{} - starting completion (attempt: {})", id(), attempt + 1)

    val history = history ?: emptyList()
    val llmInput = listOf(ChatMessage.system(context())) + history + userMessage + messageBuffer

    val completion =
      inferenceProvider.chatCompletion(
        messages = llmInput,
        config =
          ChatCompletionParameters(
            base = completionParameters,
            agent = params?.copy(tools = if (attempt < maxToolAttempts) params.tools else null),
          ),
      )

    completion.tokenUsage?.let { tokenUsage ->
      tokenTracker.store(
        amount = tokenUsage,
        usageType = TokenUsageType.COMPLETION,
        model = model,
        provider = inferenceProvider.id(),
      )
    }

    val message = completion.choices.first().message

    messageBuffer.add(message)

    if (message.toolCalls != null) {
      handleToolCalls(message.toolCalls, params!!.tools!!, messageBuffer, emitter)

      completionInner(
        userMessage = userMessage,
        messageBuffer = messageBuffer,
        params = params,
        emitter = emitter,
        attempt = attempt + 1,
      )
    } else if (message.content == null) {
      log.warn("{} - assistant message has no content and no tools have been called", id())
    }
  }

  /**
   * Process the tool calls using the handler of this instance.
   *
   * @return `true` if inference should continue, `false` if it should stop.
   */
  private suspend fun handleToolCalls(
    toolCalls: List<ToolCallData>,
    tools: Tools,
    messageBuffer: MutableList<ChatMessage>,
    emitter: Emitter?,
  ) {
    for (toolCall in toolCalls) {
      emitter?.emit(ToolEvent.ToolCall(toolCall), ToolEvent.serializer())
      try {
        val result = tools.processToolCall(toolCall)
        emitter?.emit(ToolEvent.ToolResult(result), ToolEvent.serializer())
        messageBuffer.add(ChatMessage.toolResult(result, toolCall.id))
      } catch (e: Throwable) {
        log.error("error in tool call", e)
        val result = "error: ${e.message}"
        emitter?.emit(ToolEvent.ToolResult(result), ToolEvent.serializer())
        messageBuffer.add(ChatMessage.toolResult(result, toolCall.id))
      }
    }
  }
}
