package net.barrage.llmao.core.workflow

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.ToolCallData
import net.barrage.llmao.core.llm.ToolEvent
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KUUID

/**
 * Implementation of a workflow that is essentially a runtime for a conversation with a single
 * agent.
 *
 * If a workflow is conversation based, i.e. does not have structured input and only accepts text
 * messages with attachments, this class can be used as the base.
 *
 * Structured workflows should implement [Workflow] directly.
 */
abstract class ChatWorkflowBase<I: WorkflowInput, S>(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val user: User,

  /** LLM tools. */
  protected val toolchain: Toolchain<S>?,

  /** Encapsulates the agent and its LLM functionality. */
  open val agent: WorkflowAgent,

  /** Output handle. Only chat related events are sent via this reference. */
  protected val emitter: Emitter,

  /** Whether to call the LLM in streaming mode. */
  protected val streamingEnabled: Boolean = true,

  /**
   * Deserializes the workflow input to the specified input type.
   */
  private val inputSerializer: KSerializer<I>
) : Workflow, AgentEventHandler {
  /**
   * If this is not null, it means a stream is currently running and additional input will throw
   * until the stream is complete.
   */
  private var stream: Job? = null

  /** Scope in which [stream] and [onInteractionComplete] run. */
  private val scope = CoroutineScope(Dispatchers.Default)

  protected open val log = KtorSimpleLogger("n.b.l.c.workflow.ChatWorkflowBase")

  /**
   * Callback that runs when an interaction with an LLM is complete.
   *
   * Takes as input the original user message, the attachments that were sent with it, and all the
   * messages that occurred between the prompt and the LLM response.
   *
   * Has to return the processed message group and all the attachments that were processed so they
   * can be sent back to the client.
   */
  abstract suspend fun onInteractionComplete(
    /** Original unmodified user message. */
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup

  /** Uses [emitter] to emit each chunk with content of an LLM stream. */
  override suspend fun onStreamChunk(chunk: ChatMessageChunk) {
    chunk.content?.let {
      emitter.emit(WorkflowOutput.StreamChunk(it), WorkflowOutput.serializer())
    }
  }

  /** Uses [emitter] to emit any assistant message with content. */
  override suspend fun onMessage(message: ChatMessage) {
    if (message.role == "assistant") {
      message.content?.let {
        emitter.emit(WorkflowOutput.Response(it.text()), WorkflowOutput.serializer())
      }
    }
  }

  override suspend fun onToolCalls(toolCalls: List<ToolCallData>): List<ChatMessage>? {
    val results = mutableListOf<ChatMessage>()
    for (toolCall in toolCalls) {
      emitter.emit(ToolEvent.ToolCall(toolCall), ToolEvent.serializer())
      try {
        val result = toolchain!!.processToolCall(toolCall)
        emitter.emit(ToolEvent.ToolResult(result), ToolEvent.serializer())
        results.add(ChatMessage.toolResult(result, toolCall.id))
      } catch (e: Throwable) {
        val result = "error: ${e.message}"
        emitter.emit(ToolEvent.ToolResult(result), ToolEvent.serializer())
        results.add(ChatMessage.toolResult(result, toolCall.id))
      }
    }
    return results
  }

  override fun id(): KUUID {
    return id
  }

  override fun execute(input: String) {
    val input = Json.decodeFromString(inputSerializer,input)

    if (stream != null) {
      throw AppError.api(ErrorReason.InvalidOperation, "Workflow is currently busy")
    }

    stream =
      if (streamingEnabled) scope.launch { executeStreaming(input) }
      else scope.launch { execute(input) }
  }

  private suspend fun execute(input: I) {
    input.validate()

    val streamStart = Instant.now()
    var finishReason = FinishReason.Stop

    val content =
      input.attachments()?.let { ChatMessageProcessor.toContentMulti(input.text(), it) }
        // We can safely !! because we validated the input and text must be present if
        // attachments are not
        ?: ContentSingle(input.text()!!)

    val userMessage = ChatMessage.user(content)
    val messageBuffer = mutableListOf<ChatMessage>()

    try {
      agent.completion(
        userMessage = userMessage,
        messageBuffer = messageBuffer,
        eventHandler = this,
      )
    } catch (_: CancellationException) {
      finishReason = FinishReason.ManualStop
    } catch (e: Throwable) {
      handleError(e)
    } finally {
      val lastMessage = messageBuffer.lastOrNull()

      when {
        lastMessage?.role == "tool" -> {
          // First completion and some tools were called, but the actual answer response was not
          // sent
          // Discard everything
          messageBuffer.clear()
        }
        lastMessage?.role == "assistant" && lastMessage.content == null -> {
          // The captured response is a tool call
          // Discard everything
          messageBuffer.clear()
        }
      }

      stream = null

      if (messageBuffer.isNotEmpty()) {
        val assistantMessage = messageBuffer.last()

        assert(assistantMessage.role == "assistant")
        assert(assistantMessage.finishReason != null)
        assert(assistantMessage.content != null)

        finishReason = assistantMessage.finishReason!!

        agent.addToHistory(messages = listOf(userMessage) + messageBuffer)

        scope.launch {
          val processedMessageGroup =
            onInteractionComplete(
              userMessage = userMessage,
              attachments = input.attachments(),
              messages = messageBuffer,
            )

          emitter.emit(
            WorkflowOutput.StreamComplete(
              chatId = id,
              reason = finishReason,
              messageGroupId = processedMessageGroup.messageGroupId,
              attachmentPaths = processedMessageGroup.attachments,
            ),
            WorkflowOutput.serializer(),
          )
        }
      } else {
        emitter.emit(
          WorkflowOutput.StreamComplete(chatId = id, reason = finishReason),
          WorkflowOutput.serializer(),
        )
      }

      log.debug(
        "{} - workflow response emitted ({}ms), finish reason: {}",
        user.id,
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
        finishReason.value,
      )
    }
  }

  private suspend fun executeStreaming(input: I) {
    input.validate()

    val streamStart = Instant.now()
    var finishReason = FinishReason.Stop

    val content =
      input.attachments()?.let { ChatMessageProcessor.toContentMulti(input.text(), it) }
        ?: ContentSingle(input.text()!!)

    val userMessage = ChatMessage.user(content)
    val messageBuffer = mutableListOf<ChatMessage>()
    val responseBuffer = StringBuilder()

    try {
      agent.stream(
        userMessage = userMessage,
        messageBuffer = messageBuffer,
        out = responseBuffer,
        eventHandler = this,
      )
    } catch (_: CancellationException) {
      // If the stream is cancelled from outside, a
      // CancellationException is thrown with a specific message indicating the reason.
      finishReason = FinishReason.ManualStop
    } catch (e: Throwable) {
      handleError(e)
    } finally {
      // Here we have to make sure the last message in the buffer is the assistant's.
      // If the stream is cancelled at any point before the last assistant message started
      // streaming all messages in the buffer are discarded.
      val lastMessage = messageBuffer.lastOrNull()

      when {
        lastMessage == null -> {
          if (responseBuffer.isNotBlank()) {
            // Completion was cancelled before it could finish
            messageBuffer.add(
              ChatMessage.assistant(
                content = responseBuffer.toString(),
                finishReason = finishReason,
              )
            )
          }
        }
        lastMessage.role == "tool" && responseBuffer.isBlank() -> {
          // First stream and some tools were called, but the actual answer stream did not start
          // Discard everything
          messageBuffer.clear()
        }
        lastMessage.role == "tool" && responseBuffer.isNotBlank() -> {
          // Completion was cancelled before it could finish
          messageBuffer.add(
            ChatMessage.assistant(content = responseBuffer.toString(), finishReason = finishReason)
          )
        }
        lastMessage.role == "assistant" && lastMessage.content == null -> {
          // The captured response is a tool call
          // Discard everything
          messageBuffer.clear()
        }
        lastMessage.role == "assistant" -> {
          // Completion was cancelled at a point where the LLM stream completed
          // We are certain the response is cleared, since adding the message
          // and clearing the buffer is done in a blocking manner
          assert(responseBuffer.isBlank())
        }
      }

      stream = null

      if (messageBuffer.isNotEmpty()) {
        val assistantMessage = messageBuffer.last()

        assert(assistantMessage.role == "assistant")
        assert(assistantMessage.finishReason != null)

        finishReason = assistantMessage.finishReason!!

        agent.addToHistory(messages = listOf(userMessage) + messageBuffer)

        scope.launch {
          val processedMessageGroup =
            onInteractionComplete(
              userMessage = userMessage,
              attachments = input.attachments(),
              messages = messageBuffer,
            )

          emitter.emit(
            WorkflowOutput.StreamComplete(
              chatId = id,
              reason = finishReason,
              messageGroupId = processedMessageGroup.messageGroupId,
              attachmentPaths = processedMessageGroup.attachments,
            ),
            WorkflowOutput.serializer(),
          )
        }
      } else {
        emitter.emit(
          WorkflowOutput.StreamComplete(chatId = id, reason = finishReason),
          WorkflowOutput.serializer(),
        )
      }

      log.debug(
        "{} - streaming workflow response emitted ({}ms), finish reason: {}",
        user.id,
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
        finishReason.value,
      )
    }
  }

  override fun cancelStream() {
    if (stream == null || stream?.isCancelled == true) {
      return
    }
    log.debug("{} - cancelling stream", id)
    stream!!.cancel()
  }

  /**
   * An API error will be sent if it is an application error.
   *
   * Internal otherwise.
   */
  private suspend fun handleError(e: Throwable) {
    when (e) {
      is AppError -> {
        log.error("{} - error in stream", id, e)
        emitter.emit(e.withDisplayMessage(agent.errorMessage()))
      }
      else -> {
        log.error("{} - unexpected error in stream", id, e)
        emitter.emit(AppError.internal().withDisplayMessage(agent.errorMessage()))
      }
    }
  }
}

data class ProcessedMessageGroup(
  val messageGroupId: KUUID,
  val attachments: List<MessageAttachment>? = null,
)
