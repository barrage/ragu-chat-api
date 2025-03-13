package net.barrage.llmao.core.model

import kotlinx.serialization.Serializable

const val DEFAULT_TITLE_INSTRUCTION =
  """
      |You answer only with a short and concise title that you create based on the conversation you receive.
      |The input will always be in the form "USER: <prompt>\nASSISTANT: <response>".
      |Use the user's prompt content and the assistant's response content to generate a single sentence that describes the interaction.
      |For example, given the input "USER: What is the capital of Croatia?\nASSISTANT: The capital of Croatia is Zagreb."
      |you will generate the title "The capital of Croatia".
    """

@Serializable
data class AgentInstructions(
  /**
   * Used in chats - specifies the instructions for an LLM on how to generate a chat title. Usually
   * the default directive will be fine, but it's nice to have an option to change this if the
   * models are consistently giving out bad output.
   */
  val titleInstruction: String? = null,

  /** If present, contains a message that will be sent to the user when an error occurs. */
  val errorMessage: String? = null,
) {
  fun titleInstruction(): String {
    if (titleInstruction != null) {
      return titleInstruction
    }

    return DEFAULT_TITLE_INSTRUCTION.trimMargin()
  }

  fun errorMessage(): String {
    return errorMessage ?: "An error occurred. Please try again later."
  }
}
