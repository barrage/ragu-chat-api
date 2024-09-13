package net.barrage.llmao.llm.types

import net.barrage.llmao.enums.Languages
import net.barrage.llmao.serializers.KUUID

class ChatConfig(
    val id: KUUID,
    val userId: KUUID,
    var agentId: Int,
    var title: String? = null,
    val maxHistory: Int? = null,
    val summarizeAfterTokens: Int? = null,
    var languages: Languages = Languages.CRO,
    var messageReceived: Boolean? = null,
)