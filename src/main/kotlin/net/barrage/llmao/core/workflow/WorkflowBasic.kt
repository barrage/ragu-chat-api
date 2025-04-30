package net.barrage.llmao.core.workflow

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.chat.ChatMessageProcessor
import net.barrage.llmao.core.llm.ChatCompletionAgentParameters
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ChatMessageChunk
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.llm.Tools
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.types.KUUID

/**
 * Implementation of a workflow that is for a conversation with a single agent.
 *
 * If a workflow is conversation based, i.e. does not have structured input and only accepts text
 * messages with attachments, this class can be used as the base.
 *
 * Structured workflows should implement [Workflow] directly.
 */
abstract class WorkflowBasic(
  /** Workflow ID. */
  val id: KUUID,

  /** The proompter. */
  val user: User,

  /** Encapsulates the agent and its LLM functionality. */
  open val agent: WorkflowAgent,
  val tools: Tools? = null,

  /** Output handle. */
  protected val emitter: Emitter,

  /** Whether to call the LLM in streaming mode. */
  protected val streamingEnabled: Boolean = true,
) : Workflow {
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

  override fun id(): KUUID {
    return id
  }

  override fun execute(input: String) {
    if (stream != null) {
      throw AppError.api(ErrorReason.InvalidOperation, "Workflow is currently busy")
    }

    val input = Json.decodeFromString<WorkflowInput>(input)

    input.validate()

    val streamStart = Instant.now()
    stream =
      scope.launch {
        val content =
          input.attachments?.let { ChatMessageProcessor.toContentMulti(input.text, it) }
            // We can safely !! because we validated the input and text must be present if
            // attachments are not
            ?: ContentSingle(input.text!!)

        val userMessage = ChatMessage.user(content)

        var (finishReason, messageBuffer) =
          if (streamingEnabled) executeStreaming(userMessage) else executeCompletion(userMessage)

        if (messageBuffer.isEmpty()) {
          emitter.emit(
            WorkflowOutput.StreamComplete(chatId = id, reason = finishReason),
            WorkflowOutput.serializer(),
          )
          stream = null
          return@launch
        }

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
              attachments = input.attachments,
              messages = messageBuffer,
            )

          emitter.emit(
            WorkflowOutput.StreamComplete(
              chatId = id,
              reason = finishReason,
              messageGroupId = processedMessageGroup.messageGroupId,
              attachmentPaths = processedMessageGroup.attachments,
              content = assistantMessage.content!!.text(),
            ),
            WorkflowOutput.serializer(),
          )
        }

        log.debug(
          "{} - streaming workflow response emitted ({}ms), finish reason: {}",
          user.id,
          Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          finishReason.value,
        )
        stream = null
      }
  }

  suspend fun executeCompletion(userMessage: ChatMessage): Pair<FinishReason, List<ChatMessage>> {
    var finishReason = FinishReason.Stop
    val messageBuffer = mutableListOf<ChatMessage>()

    try {
      agent.completion(
        userMessage = userMessage,
        messageBuffer = messageBuffer,
        params = ChatCompletionAgentParameters(tools = tools),
        emitter = emitter,
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
    }

    return Pair(finishReason, messageBuffer)
  }

  suspend fun executeStreaming(userMessage: ChatMessage): Pair<FinishReason, List<ChatMessage>> {
    var finishReason = FinishReason.Stop

    val messageBuffer = mutableListOf<ChatMessage>()
    val responseBuffer = StringBuilder()

    suspend fun handler(chunk: ChatMessageChunk) {
      chunk.content?.let {
        emitter.emit(WorkflowOutput.StreamChunk(chunk.content), WorkflowOutput.serializer())
      }
    }
    try {
      agent.stream(
        userMessage = userMessage,
        messageBuffer = messageBuffer,
        out = responseBuffer,
        params = ChatCompletionAgentParameters(tools = tools),
        emitter = emitter,
        streamHandler = ::handler,
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
    }

    return Pair(finishReason, messageBuffer)
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
