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
   * If present, contains instructions for the LLM on how to summarize conversations when a chat's
   * maximum history is reached.
   */
  val summaryInstruction: String? = null,
) {
  fun formatTitlePrompt(proompt: String, response: String): String {
    if (titleInstruction != null) {
      return "$titleInstruction\nPrompt: $proompt\nResponse: $response\nTitle:"
    }

    return """
       |Create a short title based on the examples below.
       |The examples below are in english language.
       |Generate the title as a single statement in english language.

       |Prompt: What is TCP?
       |Response: Transmission Control Protocol is a protocol used for reliable data transmission over the internet. It is used to ensure that data is not lost or corrupted during transmission.
       |Title: Explaining TCP
       |Prompt: I am Bedga
       |Response: Bedga is a person. Bedga writes code, creates music, and is a great person. Bedga is born in Croatia.
       |Title: Conversation about Bedga
       |Prompt: $proompt
       |Response: $response
       |Title:
    """
      .trimMargin()
  }

  fun formatSummaryPrompt(history: String): String {
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
