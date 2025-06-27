package net.barrage.llmao.chat.whatsapp.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KUUID

@Serializable data class WhatsAppAgentUpdate(val agentId: KUUID)
