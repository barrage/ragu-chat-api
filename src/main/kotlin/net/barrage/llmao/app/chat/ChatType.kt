package net.barrage.llmao.app.chat

enum class ChatType(val value: String) {
  /** Represents a conversation with a RAG agent via the Kappi API. */
  CHAT("CHAT"),

  /** Represents a conversation with a RAG agent in the context of WhatsApp. */
  WHATSAPP("WHATSAPP"),
}
