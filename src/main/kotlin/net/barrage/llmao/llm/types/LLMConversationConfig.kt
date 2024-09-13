package net.barrage.llmao.llm.types

import net.barrage.llmao.enums.LLMModels
import net.barrage.llmao.enums.Languages

data class LLMConversationConfig(
    val chat: LLMConfigChat,
    val model: LLMModels,
    val language: Languages,
)