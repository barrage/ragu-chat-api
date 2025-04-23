package net.barrage.llmao.app.workflow.chat.model

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.model.MessageGroup
import net.barrage.llmao.types.KUUID

@Serializable data class ChatMessageGroup(val group: MessageGroup, val agentConfigurationId: KUUID)
