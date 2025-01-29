package net.barrage.llmao.core.models

import kotlinx.serialization.Serializable

const val DEFAULT_TITLE_INSTRUCTION =
  """
      |You answer only with short and concise titles that you create based on the inputs you receive.
      |The inputs will always be in the form of a prompt, followed by a response from an agent.
      |Use the prompt content and the response content to generate a single sentence that describes the interaction.
      |For example, given the input \"PROMPT: What is the capital of Croatia?\nAGENT: The capital of Croatia is Zagreb.\"
      |you will generate the title \"The capital of Croatia\".
    """

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
  fun titleInstruction(): String {
    if (titleInstruction != null) {
      return titleInstruction
    }

    return DEFAULT_TITLE_INSTRUCTION.trimMargin()
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
