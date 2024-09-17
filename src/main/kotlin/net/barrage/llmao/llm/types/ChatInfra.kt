package net.barrage.llmao.llm.types

import net.barrage.llmao.llm.PromptFormatter
import net.barrage.llmao.llm.conversation.ConversationLlm
import net.barrage.llmao.websocket.Emitter

class ChatInfra(
    val llm: ConversationLlm,
    val formatter: PromptFormatter,
    val emitter: Emitter? = null,
)