package net.barrage.llmao.app.api.ws

import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.exception.InvalidRequestException
import io.ktor.util.logging.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import net.barrage.llmao.core.llm.ChatMessage
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.api.ws.Chat")

class Chat(
  private val service: ChatService,

  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  private val userId: KUUID,

  /** Who the user is chatting with. */
  private val agentId: KUUID,

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
   * @param emitter The emitter to use to send the response.
   */
  fun startStreaming(message: String, emitter: Emitter) {
    if (isStreaming()) {
      throw AppError.api(ErrorReason.Websocket, "Chat is already streaming")
    }

    CoroutineScope(Dispatchers.Default).launch { stream(message, emitter) }
  }

  /**
   * Respond to a message without streaming.
   *
   * @param proompt The message to respond to.
   * @param emitter The emitter to use to send the response.
   */
  suspend fun respond(proompt: String, emitter: Emitter) {
    val response =
      try {
        service.chatCompletion(proompt, history, agentId)
      } catch (e: AppError) {
        return handleError(emitter, e, false)
      }

    if (!messageReceived) {
      persist()
      messageReceived = true
    }

    processResponse(proompt, response, false, emitter)
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
  fun closeStream() {
    LOG.debug("Closing stream for '{}'", id)
    streamActive = false
  }

  private fun persist() {
    service.storeChat(id, userId, agentId, title)
  }

  private suspend fun stream(proompt: String, emitter: Emitter) = coroutineScope {
    streamActive = true

    val stream =
      try {
        service.chatCompletionStream(proompt, history, agentId)
      } catch (e: AppError) {
        return@coroutineScope handleError(emitter, e, true)
      }

    if (!messageReceived) {
      persist()
      messageReceived = true
    }

    try {
      val response = collectStream(proompt, stream, emitter)
      LOG.debug("Chat '{}' got response: {}", id, response)
    } catch (e: InvalidRequestException) {
      // TODO: Failure
      service.processFailedMessage(id, userId, proompt, FinishReason.ContentFilter.value)
    } catch (e: Throwable) {
      handleError(emitter, e, true)
    } finally {
      streamActive = false
    }
  }

  private suspend fun processResponse(
    proompt: String,
    response: String,
    streaming: Boolean,
    emitter: Emitter,
  ) {
    val messages: List<ChatMessage> =
      listOf(ChatMessage.user(proompt), ChatMessage.assistant(response))

    addToHistory(messages)

    val (_, assistantMsg) = service.processMessagePair(id, userId, agentId, proompt, response)

    val emitPayload =
      ServerMessage.FinishEvent(
        id,
        messageId = assistantMsg.id,
        reason = FinishReason.Stop,
        content =
          if (!streaming) {
            response
          } else {
            null
          },
      )

    emitter.emitStop()
    emitter.emitServer(emitPayload)

    if (title.isNullOrBlank()) {
      this.generateTitle(proompt, emitter)
    }
  }

  private suspend fun generateTitle(prompt: String, emitter: Emitter) {
    val title = service.generateTitle(id, prompt, agentId)
    emitter.emitServer(ServerMessage.ChatTitle(id, title))
    this.title = title
  }

  private suspend fun addToHistory(messages: List<ChatMessage>) {
    this.history.addAll(messages)

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

  private suspend fun collectStream(
    prompt: String,
    stream: Flow<List<TokenChunk>>,
    emitter: Emitter,
  ): String = coroutineScope {
    val buf: MutableList<String> = mutableListOf()

    stream.collect { tokens ->
      if (!streamActive) {
        val finalResponse = buf.joinToString("")
        if (finalResponse.isNotBlank()) {
          processResponse(prompt, finalResponse, true, emitter)
        }

        cancel()
        return@collect
      }

      for (chunk in tokens) {
        if (chunk.content.isNullOrBlank() && chunk.stopReason != FinishReason.Stop) {
          continue
        }

        // Sometimes the first and last chunks are only one whitespace
        if (!chunk.content.isNullOrBlank()) {
          emitter.emitChunk(chunk)
          buf.add(chunk.content)
        }

        if (chunk.stopReason == FinishReason.Stop) {
          break
        }
      }
    }

    val finalResponse = buf.joinToString("")

    if (finalResponse.isNotBlank()) {
      processResponse(prompt, finalResponse, true, emitter)
    }

    finalResponse
  }

  suspend fun handleError(emitter: Emitter, e: Throwable, streaming: Boolean) {
    if (streaming) {
      emitter.emitStop()
    }

    if (e is AppError) {
      emitter.emitError(e)
    } else {
      emitter.emitError(AppError.internal())
    }
  }
}
