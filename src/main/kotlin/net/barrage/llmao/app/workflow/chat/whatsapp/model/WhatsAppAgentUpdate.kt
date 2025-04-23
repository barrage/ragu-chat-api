package net.barrage.llmao.app.workflow.chat.whatsapp.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.types.KUUID

@Serializable data class WhatsAppAgentUpdate(val agentId: KUUID)
