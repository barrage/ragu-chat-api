package net.barrage.llmao.core.llm

import com.knuddels.jtokkit.api.Encoding

/**
 * Represents a conversation history and handles all trimming operations based on concrete instance.
 * The history should never include the agent's context, only the interactions with the LLM. Tool
 * calls also spend tokens, which is why it's important to include them in the history.
 */
abstract class ChatHistory(internal open val messages: MutableList<ChatMessage>) :
  Iterable<ChatMessage> {

  override fun iterator(): Iterator<ChatMessage> = messages.iterator()

  abstract fun add(messages: List<ChatMessage>)

  fun messages(): List<ChatMessage> = messages
}

/**
 * History management strategy based on tokens. Does not count tokens spent on message attachments.
 */
class TokenBasedHistory(
  internal override val messages: MutableList<ChatMessage>,
  private val tokenizer: Encoding,
  private val maxTokens: Int,
) : ChatHistory(messages) {
  private var currentTokens: Int =
    messages.fold(0) { acc, message ->
      var tokenCount = message.content?.let { tokenizer.countTokensOrdinary(it.text()) } ?: 0
      message.toolCalls?.let {
        tokenCount += it.sumOf { tokenizer.countTokensOrdinary(it.arguments) }
      }
      acc + tokenCount
    }

  override fun add(messages: List<ChatMessage>) {
    assert(messages.isNotEmpty()) { "Cannot add empty list of messages to history" }
    assert(messages.first().role == "user") { "First message must be from the user" }
    assert(messages.last().role == "assistant") { "Last message must be from the assistant" }

    val messageTokens =
      messages.fold(0) { acc, message ->
        var tokenCount = message.content?.let { tokenizer.countTokensOrdinary(it.text()) } ?: 0
        message.toolCalls?.let {
          tokenCount += it.sumOf { tokenizer.countTokensOrdinary(it.arguments) }
        }
        acc + tokenCount
      }

    trimToFit(messageTokens)
    currentTokens += messageTokens
    this.messages.addAll(messages)
  }

  private fun trimToFit(tokens: Int) {
    while (
      // Remove until we are within token limit and we start with a user message
      messages.isNotEmpty() &&
        (currentTokens + tokens > maxTokens || messages.first().role != "user")
    ) {
      val removed = messages.removeFirst()
      var removedTokens = removed.content?.let { tokenizer.countTokensOrdinary(it.text()) } ?: 0
      removed.toolCalls?.let {
        removedTokens += it.sumOf { tokenizer.countTokensOrdinary(it.arguments) }
      }
      currentTokens -= removedTokens
    }
  }
}

/**
 * History management strategy based on message amount. [TokenBasedHistory] should always be
 * preferred, this one should only be used as a fallback.
 */
class MessageBasedHistory(
  internal override val messages: MutableList<ChatMessage> = mutableListOf(),
  private val maxMessages: Int = 10,
) : ChatHistory(messages) {
  override fun add(messages: List<ChatMessage>) {
    this.messages.addAll(messages)
    trimToFit()
  }

  private fun trimToFit() {
    while (
      messages.isNotEmpty() && (messages.size > maxMessages || messages.first().role != "user")
    ) {
      messages.removeFirst()
    }
  }
}
