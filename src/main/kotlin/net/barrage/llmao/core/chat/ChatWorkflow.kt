package net.barrage.llmao.core.chat

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ServiceState
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.ContentSingle
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.IncomingMessageAttachment
import net.barrage.llmao.core.model.MessageAttachment
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.Emitter
import net.barrage.llmao.core.workflow.Workflow

private val LOG = KtorSimpleLogger("net.barrage.llmao.core.workflow.chat.ChatWorkflow")

/** Implementation of a workflow with a single agent. */
class ChatWorkflow(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val user: User,

  /** Output handle. Only chat related events are sent via this reference. */
  private val emitter: Emitter<ChatWorkflowMessage>,

  /** Encapsulates the agent and its LLM functionality. */
  private val agent: ChatAgentStreaming<ServiceState>,

  /** Responsible for persisting chat data. */
  private val repository: ChatRepositoryWrite,

  /** The current state of this workflow. */
  private var state: ChatWorkflowState,
  private val messageProcessor: ChatMessageProcessor,
) : Workflow<ChatWorkflowInput> {
  private var stream: Job? = null
  private val scope = CoroutineScope(Dispatchers.Default)

  override fun id(): KUUID {
    return id
  }

  override fun entityId(): KUUID {
    return agent.agentId
  }

  override fun execute(input: ChatWorkflowInput) {
    stream =
      scope.launch {
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
          LOG.debug(
            "{} - stream complete, took {}ms",
            agent.agentId,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          )
        } catch (_: CancellationException) {
          // If the stream is cancelled from outside, a
          // CancellationException is thrown with a specific message indicating the reason.
          LOG.debug(
            "{} - stream cancelled, took {}ms",
            agent.agentId,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          )
          finishReason = FinishReason.ManualStop
        } catch (e: AppError) {
          LOG.error("{} - error in stream", id, e)
          handleError(e)
        } catch (e: Throwable) {
          LOG.error("{} - unexpected error in stream", id, e)
          handleError(e)
        } finally {
          // Here we have to make sure the last message in the buffer is the assistant's.
          // If the stream is cancelled at any point before the last assistant message started
          // streaming all messages in the buffer are discarded.
          val lastMessage = messageBuffer.lastOrNull()

          if (lastMessage == null) {
            if (responseBuffer.isNotBlank()) {
              // Completion was cancelled before it could finish
              messageBuffer.add(
                ChatMessage.assistant(
                  content = responseBuffer.toString(),
                  finishReason = finishReason,
                )
              )
            }
          } else if (lastMessage.role == "tool" && responseBuffer.isBlank()) {
            // First stream and some tools were called, but the actual answer stream did not start
            // Discard everything
            messageBuffer.clear()
          } else if (lastMessage.role == "tool" && responseBuffer.isNotBlank()) {
            // Completion was cancelled before it could finish
            messageBuffer.add(
              ChatMessage.assistant(
                content = responseBuffer.toString(),
                finishReason = finishReason,
              )
            )
          } else if (lastMessage.role == "assistant" && lastMessage.content == null) {
            // The captured response is a tool call
            // Discard everything
            messageBuffer.clear()
          } else if (lastMessage.role == "assistant") {
            // Completion was cancelled at a point where the LLM stream completed
            // We are certain the response is cleared, since adding the message
            // and clearing the buffer is done in a blocking manner
            assert(responseBuffer.isBlank())
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
                processMessageGroup(
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

          LOG.debug(
            "{} - stream complete emitted ({}ms), finish reason: {}",
            agent.agentId,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
            finishReason.value,
          )
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

  /** Persists messages depending on chat state. */
  private suspend fun processMessageGroup(
    /** Original unmodified user message. */
    userMessage: ChatMessage,
    attachments: List<IncomingMessageAttachment>?,
    messages: List<ChatMessage>,
  ): ProcessedMessageGroup {
    val originalPrompt = userMessage.content!!.text()
    val attachmentsInsert = attachments?.let { messageProcessor.storeMessageAttachments(it) }
    val userMessageInsert = userMessage.toInsert(attachmentsInsert)

    val messagesInsert = listOf(userMessageInsert) + messages.map { it.toInsert() }

    val assistantMessage = messages.last()
    assert(assistantMessage.role == "assistant") { "Last message must be from the assistant" }
    assert(assistantMessage.content != null) { "Last assistant message must have content" }

    return when (state) {
      ChatWorkflowState.New -> {
        LOG.debug("{} - persisting chat with message pair", id)

        val groupId =
          repository.insertChatWithMessages(
            chatId = id,
            userId = user.id,
            username = user.username,
            agentId = agent.agentId,
            agentConfigurationId = agent.configurationId,
            messages = messagesInsert,
          )

        val title = agent.createTitle(originalPrompt, assistantMessage.content!!.text())

        LOG.debug("{} - generated title ({})", id, title)

        repository.updateTitle(id, user.id, title)
        emitter.emit(ChatWorkflowMessage.ChatTitleUpdated(id, title))
        state = ChatWorkflowState.Persisted(title)

        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
      is ChatWorkflowState.Persisted -> {
        LOG.debug("{} - persisting message pair", id)
        val groupId =
          repository.insertMessages(
            chatId = id,
            agentConfigurationId = agent.configurationId,
            messages = messagesInsert,
          )
        ProcessedMessageGroup(groupId, attachmentsInsert)
      }
    }
  }

  /**
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

data class ChatWorkflowInput(
  val text: String,
  val attachments: List<IncomingMessageAttachment>? = null,
)

private data class ProcessedMessageGroup(
  val messageGroupId: KUUID,
  val attachments: List<MessageAttachment>? = null,
)
