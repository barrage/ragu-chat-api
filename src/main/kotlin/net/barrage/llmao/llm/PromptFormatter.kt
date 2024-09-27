package net.barrage.llmao.llm

import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.models.Language

class PromptFormatter(private val context: String, private val language: Language) {
  fun systemMessage(): ChatMessage {
    return ChatMessage.system(context)
  }

  fun userMessage(prompt: String, documentation: String): ChatMessage {
    return ChatMessage.user(proompt(prompt, documentation))
  }

  fun title(message: String): String {
    return when (language) {
      Language.CRO -> TITLE_CRO(message)
      Language.ENG -> TITLE_ENG(message)
    }
  }

  fun summary(history: String): String {
    return when (language) {
      Language.CRO -> SUMMARIZE_CRO(history)
      Language.ENG -> SUMMARIZE_ENG(history)
    }
  }

  private fun proompt(proompt: String, documentation: String): String {
    val promptLanguage = createLanguagePrompt(language.language)
    return this.createPrompt(proompt, documentation, promptLanguage)
  }

  private fun createLanguagePrompt(languageDirective: String): String {
    return LANGUAGE(languageDirective)
  }

  private fun createPrompt(message: String, documentation: String, languagePrompt: String): String {
    return PROMPT(message, documentation, languagePrompt)
  }
}

private fun PROMPT(message: String, documentation: String, languagePrompt: String) =
  """
Use the relevant information below, as well as the information from the current conversation to answer the prompt below.
If you can answer the prompt based on the current conversation, do so without referring to the relevant information.
The user is not aware of the relevant information. Do not refer to the relevant information unless explicitly asked.
$languagePrompt

Relevant information: ###
$documentation
###

Prompt: ###
$message
###
"""

private fun LANGUAGE(languageDirective: String) =
  """
You do not speak any language other than $languageDirective. You will respond to the prompt exclusively in $languageDirective language.
You will disregard the original prompt language and answer exclusively in $languageDirective language.
You will ignore the documentation language and you translate any terms from it you use in your response to $languageDirective language.
"""
    .replace(Regex("/ {2,}/g"), " ")

private fun SUMMARIZE_CRO(history: String) =
  """
Create a summary for the conversation below denoted by triple #.
The conversation language is Croatian.
The summary language must be Croatian.

Desired format:
Summary: <summarized_conversation>

Conversation: ###
$history
###
"""

private fun SUMMARIZE_ENG(history: String) =
  """
Create a summary for the conversation below denoted by triple #.
The conversation language is English.
The summary language must also be in English.

Desired format:
Summary: <summarized_conversation>

Conversation: ###
$history
###
"""

private fun TITLE_CRO(proompt: String) =
  """
Create a short and descriptive title based on the prompt below, denoted by triple quotes.
It is very important that you output the title as a single statement in Croatian language.

Prompt: Što je TCP?
Title: Objašnjenje TCP-a
Prompt: Ja sam Bedga
Title: Razgovor o Bedgi
Prompt: \"\"\"$proompt\"\"\"
Title:
"""

private fun TITLE_ENG(proompt: String) =
  """
Create a short and descriptive title based on the prompt below, denoted by triple quotes.
It is very important that you output the title as a single statement in English language.

Prompt: What is TCP?
Title: Explaining TCP
Prompt: I am Bedga
Title: Conversation about Bedga
Prompt: \"\"\"$proompt\"\"\"
Title:
"""
