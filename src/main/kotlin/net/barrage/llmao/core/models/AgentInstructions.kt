package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentInstructions(
  /**
   * Used in chats - specifies the instructions for an LLM on how to generate a chat title. Usually
   * the default directive will be fine, but it's nice to have an option to change this if the
   * models are consistently giving out bad output.
   */
  val titleInstruction: String? = null,

  /**
   * If present, the LLM will receive instructions on what language to generate responses in. The
   * instructions will be contained in this field.
   */
  val languageInstruction: String? = null,

  /**
   * If present, contains instructions for the LLM on how to summarize conversations when a chat's
   * maximum history is reached.
   */
  val summaryInstruction: String? = null,
) {
  fun title(proompt: String, language: String): String {
    if (titleInstruction != null) {
      return "$titleInstruction\nPrompt: \"\"\"$proompt\"\"\"\nTitle:"
    }

    return """
        Create a short and descriptive title based on the prompt below, denoted by triple quotes.
        The examples below are in english language.
        You will generate the title as a single statement in $language language.

        Prompt: What is TCP?
        Title: Explaining TCP
        Prompt: I am Bedga
        Title: Conversation about Bedga
        Prompt: ${"\"\"\""}$proompt${"\"\"\""}
        Title:
    """
      .trimIndent()
  }

  fun language(language: String): String {
    if (languageInstruction != null) {
      return languageInstruction
    }

    return """
      You do not speak any language other than $language.
      You will disregard the original prompt language and answer exclusively in $language language.
    """
      .trimIndent()
  }

  fun summary(history: String, language: String): String {
    if (summaryInstruction != null) {
      return "$summaryInstruction\nConversation: \"\"\"\n$history\n\"\"\""
    }

    return """
      Create a summary for the conversation below denoted by triple quotes.
      The summary language must be in $language language.

      Desired format:
      Summary: <summarized_conversation>

      Conversation: ${"\"\"\""}
      $history
      ${"\"\"\""}
    """
      .trimIndent()
  }
}
