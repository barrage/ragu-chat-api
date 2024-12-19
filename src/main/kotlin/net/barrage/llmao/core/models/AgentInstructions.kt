package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

@Serializable
data class AgentInstructions(
  /** Base instructions to tell the LLM how to respond to prompts. */
  val promptInstruction: String? = null,

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
  fun basePrompt(): String {
    if (promptInstruction != null) {
      return promptInstruction
    }

    return """
        |Use the instructions surrounded by triple quotes to respond to the prompt surrounded by triple quotes.
        |Also use the information from the current conversation to respond if it is relevant.
        |If you do not know something, admit so."""
      .trimMargin()
  }

  fun title(proompt: String): String {
    if (titleInstruction != null) {
      return "$titleInstruction\nPrompt: \"\"\"$proompt\"\"\"\nTitle:"
    }

    return """
       |Create a short title based on the examples below.
       |The examples below are in english language.
       |Generate the title as a single statement in english language.

       |Prompt: What is TCP?
       |Title: Explaining TCP
       |Prompt: I am Bedga
       |Title: Conversation about Bedga
       |Prompt: $proompt
       |Title:
    """
      .trimMargin()
  }

  fun language(): String {
    if (languageInstruction != null) {
      return languageInstruction
    }

    return """
      |You do not speak any language other than english.
      |You will disregard the original prompt language and answer exclusively in english language.
    """
      .trimMargin()
  }

  fun summary(history: String): String {
    if (summaryInstruction != null) {
      return "$summaryInstruction\nConversation: \"\"\"\n$history\n\"\"\""
    }

    return """
      |Create a summary for the conversation below denoted by triple quotes.
      |The summary language must be in english language.

      |Desired format:
      |Summary: <summarized_conversation>

      |Conversation: ${"\"\"\""}
      |$history
      |${"\"\"\""}
    """
      .trimMargin()
  }
}
