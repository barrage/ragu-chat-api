package net.barrage.llmao.llm

import com.aallam.openai.api.core.FinishReason
import com.aallam.openai.api.exception.InvalidRequestException
import kotlinx.coroutines.flow.Flow
import net.barrage.llmao.core.services.ChatService
import net.barrage.llmao.error.internalError
import net.barrage.llmao.llm.conversation.ConversationLlm
import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.llm.types.LlmConfig
import net.barrage.llmao.llm.types.TokenChunk
import net.barrage.llmao.models.Language
import net.barrage.llmao.models.VectorQueryOptions
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
  private val agentId: Int,

  /** LLM configuration. */
  private val llmConfig: LlmConfig,

  /** LLM implementation to use. */
  private val llm: ConversationLlm,

  /** How to format prompts. */
  private val formatter: PromptFormatter,

  /** Knowledge base options. */
  private val vectorOptions: VectorQueryOptions,

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
    service.storeChat(id, userId, agentId, llmConfig, title)
  }

  suspend fun stream(proompt: String, emitter: Emitter) {
    streamActive = true

    if (!messageReceived) {
      persist()
      messageReceived = true
    }

    val query = service.prepareChatPrompt(proompt, history, formatter, vectorOptions)

    val stream: Flow<List<TokenChunk>> = llm.completionStream(query, llmConfig)

    try {
      val response = collectStream(proompt, stream, emitter)
      streamActive = false
      println("Chat '$id' got response: $response")
      processResponse(proompt, response, true, emitter)
    } catch (e: InvalidRequestException) {
      streamActive = false
      val response = contentFilterErrorMessage()
      service.processFailedMessage(id, userId, proompt, FinishReason.ContentFilter.value)
      emitter.emitFinishResponse(
        FinishEvent(id, reason = FinishReason.ContentFilter, content = response)
      )
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

    val response =
      service.chatCompletion(proompt, history, formatter, vectorOptions, llm, llmConfig)

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
    val title = service.generateTitle(id, prompt, formatter, llm, llmConfig)
    emitter.emitTitle(id, title)
  }

  private suspend fun addToHistory(messages: List<ChatMessage>) {
    this.history.addAll(messages)

    summarizeAfterTokens?.let {
      val tokenCount = service.countHistoryTokens(history, llmConfig.model)
      if (tokenCount >= it) {
        val summary = service.summarizeConversation(id, history, formatter, llm, llmConfig)
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

  private fun contentFilterErrorMessage(): String {
    return if (llmConfig.language == Language.CRO)
      "Ispričavam se, ali trenutno ne mogu pružiti odgovor. Molim vas da preformulirate svoj zahtjev ili postavite drugo pitanje."
    else
      "I apologize, but I'm unable to provide a response at this time. Please try rephrasing your request or ask something else."
  }
}
