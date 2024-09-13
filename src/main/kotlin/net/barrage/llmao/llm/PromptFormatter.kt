package net.barrage.llmao.llm

import net.barrage.llmao.enums.Languages
import net.barrage.llmao.llm.types.ChatMessage
import net.barrage.llmao.llm.types.systemChatMessage
import net.barrage.llmao.llm.types.userChatMessage

class PromptFormatter(
    val context: String,
    val language: Languages
) {
    fun systemMessage(): ChatMessage {
        return systemChatMessage(this.context)
    }

    fun userMessage(prompt: String, documentation: String): ChatMessage {
        return userChatMessage(this.proompt(prompt, documentation))
    }

    private fun proompt(proompt: String, documentation: String): String {
        val promptLanguage = this.createLanguagePrompt(this.language.language)
        return this.createPrompt(proompt, documentation, promptLanguage)
    }

    fun title(message: String): String {
        return when (this.language) {
            Languages.CRO -> TITLE_CRO(message)
            Languages.ENG -> TITLE_ENG(message)
        }
    }

    fun summary(history: String): String {
        return when (this.language) {
            Languages.CRO -> SUMMARIZE_CRO(history)
            Languages.ENG -> SUMMARIZE_ENG(history)
        }
    }

    private fun createLanguagePrompt(languageDirective: String): String {
        return LANGUAGE(languageDirective)
    }

    private fun createPrompt(
        message: String,
        documentation: String,
        languagePrompt: String,
    ): String {
        return PROMPT(message, documentation, languagePrompt)
    }
}

fun PROMPT(
    message: String,
    documentation: String,
    languagePrompt: String,
) = """
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

fun LANGUAGE(languageDirective: String) =
    """
You do not speak any language other than $languageDirective. You will respond to the prompt exclusively in $languageDirective language.
You will disregard the original prompt language and answer exclusively in $languageDirective language.
You will ignore the documentation language and you translate any terms from it you use in your response to $languageDirective language.
""".replace(
        Regex("/ {2,}/g"),
        " "
    );

fun SUMMARIZE_CRO(history: String) =
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

fun SUMMARIZE_ENG(history: String) =
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

fun TITLE_CRO(proompt: String) = """
Create a short and descriptive title based on the prompt below, denoted by triple quotes.
It is very important that you output the title as a single statement in Croatian language.

Prompt: Što je TCP?
Title: Objašnjenje TCP-a
Prompt: Ja sam Bedga
Title: Razgovor o Bedgi
Prompt: \"\"\"$proompt\"\"\"
Title:
"""

fun TITLE_ENG(proompt: String) = """
Create a short and descriptive title based on the prompt below, denoted by triple quotes.
It is very important that you output the title as a single statement in English language.

Prompt: What is TCP?
Title: Explaining TCP
Prompt: I am Bedga
Title: Conversation about Bedga
Prompt: \"\"\"$proompt\"\"\"
Title:
"""