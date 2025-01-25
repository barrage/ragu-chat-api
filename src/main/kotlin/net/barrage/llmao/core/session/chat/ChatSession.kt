package net.barrage.llmao.core.session.chat

import io.ktor.util.logging.*
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.models.FinishReason
import net.barrage.llmao.core.services.ConversationService
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

  /** Who the user is chatting with. */
  val agentId: KUUID,

  /** Output handle. Only chat related events are sent via this reference. */
  private val emitter: Emitter<ChatSessionMessage>,

  /** The main business logic delegate for chatting. */
  private val service: ConversationService,

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
  private var streamActive: Boolean = false

  override fun id(): SessionId {
    return SessionId.Chat(id)
  }

  override fun entityId(): SessionEntity {
    return SessionEntity.Agent(id)
  }

  override fun start(message: String) {
    if (isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    CoroutineScope(Dispatchers.Default).launch { stream(message) }
  }

  override fun isStreaming(): Boolean {
    return streamActive
  }

  override fun cancelStream() {
    if (streamActive) {
      LOG.debug("Closing stream for '{}'", id)
    }
    streamActive = false
  }

  override suspend fun persist() {
    if (!messageReceived) {
      // TODO: Transaction for chat and initial message
      service.storeChat(id, userId, agentId, title)
      messageReceived = true
    }
  }

  /** The actual streaming implementation. */
  private suspend fun stream(prompt: String) = coroutineScope {
    streamActive = true

    val streamStart = Instant.now()
    val stream =
      try {
        service.chatCompletionStream(prompt, history, agentId)
      } catch (e: AppError) {
        handleError(e)
        return@coroutineScope
      }

    try {
      var finishReason = FinishReason.Stop
      val response = StringBuilder()
      LOG.debug("Started stream for '{}'", id)
      try {
        collectAndForwardStream(stream, response, emitter)
        LOG.debug("Chat '{}' got response: {}", id, response)
      } catch (e: CancellationException) {
        LOG.debug(
          "Stream in '{}' cancelled, took {}ms, storing response: {}",
          id,
          Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
          response.isNotBlank(),
        )
        finishReason = FinishReason.ManualStop
      }

      if (response.isNotBlank()) {
        processResponse(
          prompt = prompt,
          response = response.toString(),
          finishReason = finishReason,
        )
      }

      LOG.debug(
        "Stream in '{}' complete, took {}ms",
        id,
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
      )
    } catch (e: Throwable) {
      LOG.error("Unexpected error in stream", e)
      handleError(e)
    } finally {
      streamActive = false
    }
  }

  private suspend fun collectAndForwardStream(
    stream: Flow<List<TokenChunk>>,
    response: StringBuilder,
    emitter: Emitter<ChatSessionMessage>,
  ) = coroutineScope {
    stream.collect { tokens ->
      if (!streamActive) {
        // Stream was manually cancelled, cancel the streaming job
        cancel()
      }

      for (chunk in tokens) {
        if (chunk.content.isNullOrEmpty() && chunk.stopReason?.value != FinishReason.Stop.value) {
          continue
        }

        if (!chunk.content.isNullOrEmpty()) {
          emitter.emit(ChatSessionMessage.StreamChunk(chunk.content))
          response.append(chunk.content)
        }

        if (chunk.stopReason?.value == FinishReason.Stop.value) {
          break
        }
      }
    }
  }

  private suspend fun processResponse(
    prompt: String,
    response: String,
    finishReason: FinishReason,
  ) {
    if (response.isEmpty()) {
      emitter.emitError(AppError.api(ErrorReason.Websocket, "Got empty response from LLM"))
      return
    }

    persist()

    if (title.isNullOrBlank()) {
      generateAndSetTitle(prompt, response)
    }

    val messages: List<ChatMessage> =
      listOf(ChatMessage.user(prompt), ChatMessage.assistant(response))

    addToHistory(messages)

    val (_, assistantMsg) = service.processMessagePair(id, userId, agentId, prompt, response)

    LOG.debug("Emitting stream complete for '{}', finish reason: {}", id, finishReason)

    val emitPayload =
      ChatSessionMessage.StreamComplete(id, messageId = assistantMsg.id, reason = finishReason)

    emitter.emit(emitPayload)
  }

  private suspend fun generateAndSetTitle(prompt: String, response: String) {
    title = service.createAndUpdateTitle(id, prompt, response, agentId)
    emitter.emit(ChatSessionMessage.ChatTitleUpdated(id, title!!))
  }

  private suspend fun addToHistory(messages: List<ChatMessage>) {
    history.addAll(messages)

    summarizeAfterTokens?.let {
      val tokenCount = service.countHistoryTokens(history, agentId)
      if (tokenCount >= it) {
        val summary = service.summarizeConversation(id, history, agentId)
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
