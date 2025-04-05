package net.barrage.llmao.core.chat

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.core.workflow.WorkflowAgent

/**
 * Implementation of a workflow that is essentially a runtime for a conversation with a single
 * agent.
 *
 * If a workflow is conversation based, i.e. does not have structured input and only accepts text
 * messages with attachments, this class should be used as the base.
 *
 * Structured workflows should implement [Workflow] directly.
 */
abstract class ConversationWorkflow(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val user: User,

  /** Encapsulates the agent and its LLM functionality. */
  protected open val agent: WorkflowAgent<*>,

  /** Output handle. Only chat related events are sent via this reference. */
  protected val emitter: Emitter<ChatWorkflowMessage>,
  protected val messageProcessor: ChatMessageProcessor,
  protected val streamingEnabled: Boolean = true,
) : Workflow {
  /**
   * If this is not null, it means a stream is currently running and additional input will throw
   * until the stream is complete.
   */
  private var stream: Job? = null

  /** Scope in which [stream] and [onInteractionComplete] run. */
  private val scope = CoroutineScope(Dispatchers.Default)

  protected open val log = KtorSimpleLogger("net.barrage.llmao.core.workflow.chat.ChatWorkflow")

  abstract suspend fun onInteractionComplete(
    /** Original unmodified user message. */
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup

  override fun id(): KUUID {
    return id
  }

  override fun agentId(): String {
    return agent.id()
  }

  override fun execute(input: String) {
    val input = Json.decodeFromString<ChatWorkflowInput>(input)

    if (stream != null) {
      throw AppError.api(ErrorReason.Websocket, "Workflow busy")
    }

    stream =
      if (streamingEnabled) scope.launch { executeStreaming(input) }
      else scope.launch { execute(input) }
  }

  private suspend fun execute(input: ChatWorkflowInput) {
    val streamStart = Instant.now()
    var finishReason = FinishReason.Stop

    val attachments =
      if (input.attachments != null && input.attachments.isEmpty()) {
        null
      } else input.attachments

    val content =
      attachments?.let { messageProcessor.toContentMulti(input.text, it) }
        ?: ContentSingle(input.text)

    val userMessage = ChatMessage.user(content)
    val messageBuffer = mutableListOf<ChatMessage>()

    try {
      agent.completion(userMessage, messageBuffer)

      log.debug(
        "{} - completion complete, took {}ms",
        agent.id(),
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
      )
    } catch (_: CancellationException) {
      log.debug(
        "{} - completion cancelled, took {}ms",
        agent.id(),
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
      )
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
              attachments = input.attachments,
              messages = messageBuffer,
            )

          emitter.emit(
            ChatWorkflowMessage.StreamComplete(
              chatId = id,
              reason = finishReason,
              messageId = processedMessageGroup.messageGroupId,
              attachmentPaths = processedMessageGroup.attachments,
            )
          )
        }
      } else {
        emitter.emit(ChatWorkflowMessage.StreamComplete(chatId = id, reason = finishReason))
      }

      log.debug(
        "{} - response emitted ({}ms), finish reason: {}",
        agent.id(),
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
        finishReason.value,
      )
    }
  }

  private suspend fun executeStreaming(input: ChatWorkflowInput) {
    val streamStart = Instant.now()
    var finishReason = FinishReason.Stop

    val attachments =
      if (input.attachments != null && input.attachments.isEmpty()) {
        null
      } else input.attachments

    val content =
      attachments?.let { messageProcessor.toContentMulti(input.text, it) }
        ?: ContentSingle(input.text)

    val userMessage = ChatMessage.user(content)
    val messageBuffer = mutableListOf<ChatMessage>()
    val responseBuffer = StringBuilder()

    try {
      agent.stream(userMessage, messageBuffer, responseBuffer)
      log.debug(
        "{} - stream complete, took {}ms",
        agent.id(),
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
      )
    } catch (_: CancellationException) {
      // If the stream is cancelled from outside, a
      // CancellationException is thrown with a specific message indicating the reason.
      log.debug(
        "{} - stream cancelled, took {}ms",
        agent.id(),
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
      )
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
              attachments = input.attachments,
              messages = messageBuffer,
            )

          emitter.emit(
            ChatWorkflowMessage.StreamComplete(
              chatId = id,
              reason = finishReason,
              messageId = processedMessageGroup.messageGroupId,
              attachmentPaths = processedMessageGroup.attachments,
            )
          )
        }
      } else {
        emitter.emit(ChatWorkflowMessage.StreamComplete(chatId = id, reason = finishReason))
      }

      log.debug(
        "{} - stream complete emitted ({}ms), finish reason: {}",
        agent.id(),
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
        emitter.emitError(e.withDisplayMessage(agent.errorMessage()))
      }
      else -> {
        log.error("{} - unexpected error in stream", id, e)
        emitter.emitError(AppError.internal().withDisplayMessage(agent.errorMessage()))
      }
    }
  }
}

@Serializable
data class ChatWorkflowInput(
  val text: String,
  val attachments: List<IncomingMessageAttachment>? = null,
)

data class ProcessedMessageGroup(
  val messageGroupId: KUUID,
  val attachments: List<MessageAttachment>? = null,
)
