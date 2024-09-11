package net.barrage.llmao.llm

import net.barrage.llmao.enums.Languages
import net.barrage.llmao.llm.conversation.ConversationLlm
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.services.ChatService
import net.barrage.llmao.websocket.Emitter


class Chat(
    val config: ChatConfig,
    val infra: ChatInfra,
    val history: MutableList<ChatMessage> = mutableListOf(),
) {
    val streamActive: Boolean = false
    val service = ChatService()
}

class ChatConfig (
    val id: KUUID,
    val userId: KUUID,
    val agentId: Int,
    val title: String? = null,
    val maxHistory: Int? = null,
    val summarizeAfterTokens: Int? = null,
    val languages: Languages = Languages.CRO,
    val messageReceived: Boolean? = null,
)

class ChatInfra (
    val llm: ConversationLlm,
    val formatter: PromptFormatter,
    val emitter: Emitter?,
)
