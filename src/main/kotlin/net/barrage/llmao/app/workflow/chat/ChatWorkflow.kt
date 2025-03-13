package net.barrage.llmao.app.workflow.chat

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow
import net.barrage.llmao.error.AppError

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.workflow.chat.ChatWorkflow")

/** Implementation of a workflow with a single agent. */
class ChatWorkflow(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val user: User,

  /** Output handle. Only chat related events are sent via this reference. */
  private val emitter: Emitter<ChatWorkflowMessage>,

  /** Encapsulates the agent and its LLM functionality. */
  private val streamAgent: ChatAgentStreaming,

  /** Responsible for persisting chat data. */
  private val repository: ChatRepositoryWrite,

  /** The current state of this workflow. */
  private var state: ChatWorkflowState,
) : Workflow {
  private var stream: Job? = null
  private val scope = CoroutineScope(Dispatchers.Default)

  override fun id(): KUUID {
    return id
  }

  override fun entityId(): KUUID {
    return streamAgent.chatAgent.agentId
  }

  override fun execute(message: String) {

    stream =
      scope.launch {
        // Temporary buffer for capturing new messages

        val streamStart = Instant.now()
        var finishReason = FinishReason.Stop

        val messageBuffer = mutableListOf<ChatMessage>(ChatMessage.user(message))
        val responseBuffer = StringBuilder()

        try {
          streamAgent.stream(messageBuffer, responseBuffer)
          LOG.debug(
            "{} - stream complete, took {}ms",
            streamAgent.chatAgent.agentId,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          )
        } catch (_: CancellationException) {
          // If the stream is cancelled from outside, a
          // CancellationException is thrown with a specific message indicating the reason.
          LOG.debug(
            "{} - stream cancelled, took {}ms",
            streamAgent.chatAgent.agentId,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          )
          finishReason = FinishReason.ManualStop
        } catch (e: AppError) {
          LOG.error("Error in stream", e)
          handleError(e)
        } catch (e: Throwable) {
          LOG.error("Unexpected error in stream", e)
          handleError(e)
        } finally {
          val lastMessage = messageBuffer.last()

          when {
            // Completion was never called
            // Discard everything
            lastMessage.role == "user" && responseBuffer.isBlank() -> {
              messageBuffer.clear()
            }
            // First stream and some tools were called, but the actual answer stream did not start
            // Discard everything
            lastMessage.role == "tool" && responseBuffer.isBlank() -> {
              messageBuffer.clear()
            }
            // Completion was cancelled before it could finish
            lastMessage.role == "user" && responseBuffer.isNotBlank() -> {
              messageBuffer.add(ChatMessage.assistant(responseBuffer.toString()))
            }
            // Completion was cancelled before it could finish
            lastMessage.role == "tool" && responseBuffer.isNotBlank() -> {
              messageBuffer.add(ChatMessage.assistant(responseBuffer.toString()))
            }
            // Completion was cancelled at a point where the LLM stream completed
            lastMessage.role == "assistant" -> {
              // We are certain the response is cleared, since adding the message
              // and clearing the buffer is done in a blocking manner
              assert(responseBuffer.isBlank())
            }
          }
          stream = null

          if (messageBuffer.isNotEmpty()) {
            streamAgent.addToHistory(messages = messageBuffer)
          }
        }

        scope.launch {
          val messageId =
            processResponse(finishReason = finishReason, messages = messageBuffer.toMutableList())

          LOG.debug(
            "{} - emitting stream complete, finish reason: {}",
            streamAgent.chatAgent.agentId,
            finishReason.value,
          )

          val emitPayload =
            ChatWorkflowMessage.StreamComplete(
              chatId = id,
              reason = finishReason,
              messageId = messageId,
            )

          emitter.emit(emitPayload)
        }
      }
  }

  override fun cancelStream() {
    if (stream == null) {
      return
    }
    LOG.debug("{} - cancelling stream", id)
    stream!!.cancel()
  }

  /** Persists messages depending on chat state. Returns the assistant message ID. */
  private suspend fun processResponse(
    messages: MutableList<ChatMessage>,
    finishReason: FinishReason,
  ): KUUID? {
    if (messages.isEmpty()) {
      return null
    }

    val userMessage = messages.first()
    assert(userMessage.role == "user") { "First message must be from the user" }

    val assistantMessage = messages.last()

    // Adjust finish reason in case of stream cancellations
    assistantMessage.finishReason = finishReason
    assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }

    val messageGroupId =
      when (state) {
        ChatWorkflowState.New -> {
          LOG.debug("{} - persisting chat with message pair", id)
          repository.insertChat(
            chatId = id,
            userId = user.id,
            username = user.username,
            agentId = streamAgent.chatAgent.agentId,
          )
          val groupId =
            repository.insertMessages(
              chatId = id,
              agentConfigurationId = streamAgent.chatAgent.configurationId,
              messages = messages.map { it.toInsert() },
            )

          LOG.debug("{} - generating title", id)
          val title =
            streamAgent.chatAgent.createTitle(
              userMessage.content ?: "",
              assistantMessage.content ?: "",
            )

          // Safe to !! because title generation never sends tools to the LLM
          repository.updateTitle(id, user.id, title.content!!)
          emitter.emit(ChatWorkflowMessage.ChatTitleUpdated(id, title.content!!))
          state = ChatWorkflowState.Persisted(title.content!!)

          groupId
        }
        is ChatWorkflowState.Persisted -> {
          LOG.debug("{} - persisting message pair", id)
          repository.insertMessages(
            chatId = id,
            agentConfigurationId = streamAgent.chatAgent.configurationId,
            messages = messages.map { it.toInsert() },
          )
        }
      }

    return messageGroupId
  }

  /**
   * An API error will be sent if it is an application error.
   *
   * Internal otherwise.
   */
  private suspend fun handleError(e: Throwable) {
    when (e) {
      is AppError -> {
        emitter.emitError(e.withDisplayMessage(streamAgent.chatAgent.instructions.errorMessage()))
      }
      else -> {
        LOG.error("Error in chat", e)
        emitter.emitError(
          AppError.internal().withDisplayMessage(streamAgent.chatAgent.instructions.errorMessage())
        )
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
