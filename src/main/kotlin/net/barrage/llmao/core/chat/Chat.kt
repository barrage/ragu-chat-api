package net.barrage.llmao.core.chat

import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.exception.InvalidRequestException
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.core.llm.PromptFormatter
import net.barrage.llmao.core.llm.TokenChunk
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.error.internalError
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.websocket.Emitter
import net.barrage.llmao.websocket.FinishEvent

class Chat(
  private val service: ChatService,

  /** Chat ID. */
  val id: KUUID,

  /** The proompter. */
  private val userId: KUUID,

  /** Who the user is chatting with. */
  private val agentId: KUUID,

  /** How to format prompts. */
  private val formatter: PromptFormatter,

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
  private var streamActive: Boolean = false

  private fun persist() {
    service.storeChat(id, userId, agentId, title)
  }

  suspend fun stream(proompt: String, emitter: Emitter) {
    streamActive = true

    if (!messageReceived) {
      persist()
      messageReceived = true
    }

    val stream = service.chatCompletionStream(proompt, history, agentId, formatter)

    try {
      val response = collectStream(proompt, stream, emitter)
      streamActive = false
      println("Chat '$id' got response: $response")
      processResponse(proompt, response, true, emitter)
    } catch (e: InvalidRequestException) {
      streamActive = false
      // TODO: Failure
      // val response = contentFilterErrorMessage()
      service.processFailedMessage(id, userId, proompt, FinishReason.ContentFilter.value)
      // emitter.emitFinishResponse(
      //  FinishEvent(id, reason = FinishReason.ContentFilter, content = response)
      // )
    } catch (e: Exception) {
      e.printStackTrace()
      streamActive = false
      emitter.emitError(internalError())
    }
  }

  suspend fun respond(proompt: String, emitter: Emitter) {
    if (!messageReceived) {
      persist()
      messageReceived = true
    }

    val response = service.chatCompletion(proompt, history, agentId, formatter)

    processResponse(proompt, response, false, emitter)
  }

  fun isStreaming(): Boolean {
    return streamActive
  }

  fun closeStream() {
    this.streamActive = false
  }

  private suspend fun processResponse(
    proompt: String,
    response: String,
    streaming: Boolean,
    emitter: Emitter,
  ): ChatMessage {
    val messages: List<ChatMessage> =
      listOf(ChatMessage.user(proompt), ChatMessage.assistant(response))

    addToHistory(messages)

    val (_, assistantMsg) = service.processMessagePair(id, userId, agentId, proompt, response)

    val emitPayload =
      FinishEvent(
        id,
        messageId = assistantMsg.id,
        reason = FinishReason.Stop,
        content = (!streaming).let { response },
      )

    emitter.emitFinishResponse(emitPayload)

    if (title.isNullOrBlank()) {
      this.generateTitle(proompt, emitter)
    }

    return ChatMessage.assistant(response)
  }

  private suspend fun generateTitle(prompt: String, emitter: Emitter) {
    val title = service.generateTitle(id, prompt, formatter, agentId)
    emitter.emitTitle(id, title)
  }

  private suspend fun addToHistory(messages: List<ChatMessage>) {
    this.history.addAll(messages)

    summarizeAfterTokens?.let {
      val tokenCount = service.countHistoryTokens(history, agentId)
      if (tokenCount >= it) {
        val summary = service.summarizeConversation(id, history, formatter, agentId)
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
  ): String {
    val buf: MutableList<String> = mutableListOf()

    stream.collect { tokens ->
      if (!streamActive) {
        println("Canceling stream for '$id'")

        if (buf.isNotEmpty()) {
          val response = buf.joinToString("")
          processResponse(prompt, response, true, emitter)
        }

        return@collect
      }

      for (chunk in tokens) {
        if (chunk.content.isNullOrEmpty() && chunk.stopReason != FinishReason.Stop) {
          continue
        }

        if (!chunk.content.isNullOrEmpty()) {
          emitter.emitChunk(chunk)
          buf.add(chunk.content)
        }

        if (chunk.stopReason == FinishReason.Stop) {
          break
        }
      }
    }

    return buf.joinToString("")
  }

  // private fun contentFilterErrorMessage(): String {
  //   return if (llmConfig.language == Language.CRO)
  //     "Ispričavam se, ali trenutno ne mogu pružiti odgovor. Molim vas da preformulirate svoj
  // zahtjev ili postavite drugo pitanje."
  //   else
  //     "I apologize, but I'm unable to provide a response at this time. Please try rephrasing your
  // request or ask something else."
  // }
}
