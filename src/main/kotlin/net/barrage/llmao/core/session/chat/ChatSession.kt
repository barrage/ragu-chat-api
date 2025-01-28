package net.barrage.llmao.core.session.chat

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
import net.barrage.llmao.core.llm.Toolchain
import net.barrage.llmao.core.session.Emitter
import net.barrage.llmao.core.session.Session
import net.barrage.llmao.core.session.SessionEntity
import net.barrage.llmao.core.session.SessionId
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.core.session.ChatSession")

/** Implementation of a workflow with a single agent. */
class ChatSession(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val userId: KUUID,

  /** LLM tools. */
  private val toolchain: Toolchain? = null,

  /** Output handle. Only chat related events are sent via this reference. */
  private val emitter: Emitter<ChatSessionMessage>,

  /** Encapsulates the agent and its LLM functionality. */
  private val sessionAgent: ChatSessionAgent,

  /**  */
  private val repository: ChatSessionRepository,

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
) : Session {
  /** True if the chat is streaming, false otherwise. */
  private var stream: Job? = null

  /** The scope in which streams will be processed. */
  private var streamScope: CoroutineScope = CoroutineScope(Dispatchers.Default)

  override fun id(): SessionId {
    return SessionId.Chat(id)
  }

  override fun entityId(): SessionEntity {
    return SessionEntity.Agent(id)
  }

  override fun startStream(message: String) {
    if (isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    stream =
      streamScope.launch {
        val streamStart = Instant.now()
        val llmStream =
          try {
            if (toolchain != null) {
              LOG.debug("{} - starting stream with tools", id)
              sessionAgent.chatCompletionStreamWithTools(message, history)
            } else {
              LOG.debug("{} - starting stream with RAG", id)
              sessionAgent.chatCompletionStreamWithRag(message, history)
            }
          } catch (e: AppError) {
            handleError(e)
            return@launch
          }

        val response = StringBuilder()
        var finishReason = FinishReason.Stop

        try {
          llmStream.collect { chunk ->
            println(chunk)

            if (!chunk.content.isNullOrEmpty()) {
              emitter.emit(ChatSessionMessage.StreamChunk(chunk.content))
              response.append(chunk.content)
            }
          }

          LOG.debug(
            "{} - stream complete, took {}ms",
            id,
            Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          )
        } catch (e: CancellationException) {
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
          if (response.isNotBlank()) {
            streamScope.launch {
              processResponse(
                prompt = message,
                response = response.toString(),
                finishReason = finishReason,
              )
            }
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

  private suspend fun processResponse(
    prompt: String,
    response: String,
    finishReason: FinishReason,
  ) {
    val assistantMessageId =
      if (!messageReceived) {
        LOG.debug("{} - persisting chat with message pair", id)
        messageReceived = true
        repository.insertChat(id, userId, prompt, sessionAgent.agent.id, response)
      } else {
        LOG.debug("{} - persisting message pair", id)
        repository.insertMessagePair(
          chatId = id,
          userId = userId,
          prompt = prompt,
          agentConfigurationId = sessionAgent.agent.configurationId,
          response,
        )
      }

    if (title.isNullOrBlank()) {
      LOG.debug("{} - generating title", id)
      title = sessionAgent.createTitle(prompt, response)
      repository.updateTitle(id, userId, title!!)
      emitter.emit(ChatSessionMessage.ChatTitleUpdated(id, title!!))
    }

    val messages: List<ChatMessage> =
      listOf(ChatMessage.user(prompt), ChatMessage.assistant(response))

    addToHistory(messages)

    LOG.debug("{} - emitting stream complete, finish reason: {}", id, finishReason)

    val emitPayload =
      ChatSessionMessage.StreamComplete(id, messageId = assistantMessageId, reason = finishReason)

    emitter.emit(emitPayload)
  }

  private suspend fun addToHistory(messages: List<ChatMessage>) {
    history.addAll(messages)

    summarizeAfterTokens?.let {
      val tokenCount = sessionAgent.countHistoryTokens(history)
      if (tokenCount >= it) {
        val summary = sessionAgent.summarizeConversation(history)
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
