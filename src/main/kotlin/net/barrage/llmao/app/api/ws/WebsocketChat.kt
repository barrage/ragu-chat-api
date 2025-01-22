package net.barrage.llmao.app.api.ws

import com.aallam.openai.api.exception.InvalidRequestException
import io.ktor.util.logging.*
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.models.FinishReason
import net.barrage.llmao.core.services.ConversationService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.api.ws")

data class Chat(
  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  val userId: KUUID,

  /** Who the user is chatting with. */
  val agentId: KUUID,

  /** Websocket handle. Only chat related events are sent via this reference. */
  private val channel: WebsocketChannel,

  /** The main business logic delegate. */
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
) {
  /** True if the chat is streaming, false otherwise. */
  private var streamActive: Boolean = false

  /**
   * Start streaming from an LLM and forward its generated chunks to the client via the emitter.
   * Creates a new coroutine to stream the response.
   *
   * @param message The proompt.
   */
  fun startStreaming(message: String) {
    if (isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    CoroutineScope(Dispatchers.Default).launch { stream(message) }
  }

  /**
   * Check if the chat is streaming.
   *
   * @return True if the chat is streaming, false otherwise.
   */
  fun isStreaming(): Boolean {
    return streamActive
  }

  /** Close the stream and cancel its job, if it exists. */
  fun cancelStream() {
    if (streamActive) {
      LOG.debug("Closing stream for '{}'", id)
    }
    streamActive = false
  }

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
      LOG.debug("Started stream for '{}'", id)
      val (response, finishReason) = collectAndForwardStream(stream, channel)

      LOG.debug(
        "Stream in '{}' complete, took {}ms",
        id,
        Instant.now().toEpochMilli() - streamStart.toEpochMilli(),
      )

      if (response.isNotBlank()) {
        processResponse(prompt = prompt, response = response, finishReason = finishReason)
        LOG.debug("Chat '{}' got response: {}", id, response)
      }
    } catch (e: InvalidRequestException) {
      // TODO: Failure
      service.processFailedMessage(id, userId, prompt, FinishReason.ContentFilter)
    } catch (e: Throwable) {
      LOG.error("Unexpected error in stream", e)
      handleError(e)
    } finally {
      streamActive = false
    }
  }

  private suspend fun processResponse(
    prompt: String,
    response: String,
    finishReason: FinishReason,
  ) {
    if (response.isEmpty()) {
      channel.emitError(AppError.api(ErrorReason.Websocket, "Got empty response from LLM"))
      return
    }

    if (!messageReceived) {
      // TODO: Transaction for chat and initial message
      service.storeChat(id, userId, agentId, title)
      messageReceived = true
    }

    if (title.isNullOrBlank()) {
      generateAndSetTitle(prompt, response)
    }

    val messages: List<ChatMessage> =
      listOf(ChatMessage.user(prompt), ChatMessage.assistant(response))

    addToHistory(messages)

    val (_, assistantMsg) = service.processMessagePair(id, userId, agentId, prompt, response)

    val emitPayload =
      ServerMessage.FinishEvent(id, messageId = assistantMsg.id, reason = finishReason)

    channel.emitServer(emitPayload)
  }

  private suspend fun generateAndSetTitle(prompt: String, response: String) {
    title = service.createAndUpdateTitle(id, prompt, response, agentId)
    channel.emitServer(ServerMessage.ChatTitle(id, title!!))
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

  private suspend fun collectAndForwardStream(
    stream: Flow<List<TokenChunk>>,
    channel: WebsocketChannel,
  ): Pair<String, FinishReason> = coroutineScope {
    val buf: MutableList<String> = mutableListOf()
    var finishReason: FinishReason = FinishReason.Stop

    stream.collect { tokens ->
      if (!streamActive) {
        // Stream was manually cancelled
        LOG.debug("Stream in '{}' cancelled, aborting | storing response: {}", id, buf.isNotEmpty())
        finishReason = FinishReason.ManualStop
        return@collect
      }

      for (chunk in tokens) {
        if (chunk.content.isNullOrEmpty() && chunk.stopReason?.value != FinishReason.Stop.value) {
          continue
        }

        // Sometimes the first and last chunks are only one whitespace
        if (!chunk.content.isNullOrEmpty()) {
          channel.emitChunk(chunk)
          buf.add(chunk.content)
        }

        if (chunk.stopReason?.value == FinishReason.Stop.value) {
          break
        }
      }
    }

    Pair(buf.joinToString(""), finishReason)
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
        channel.emitError(e)
      }
      is CancellationException -> {
        LOG.debug("Chat '{}' - stream cancelled", id)
        if (e.message == "manual_cancel") {
          return
        }
        e.printStackTrace()
        channel.emitError(AppError.internal())
      }
      else -> {
        LOG.error("Error in chat", e)
        channel.emitError(AppError.internal())
      }
    }
  }
}
