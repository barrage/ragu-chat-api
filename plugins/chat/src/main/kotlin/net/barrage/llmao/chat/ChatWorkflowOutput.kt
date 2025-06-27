package net.barrage.llmao.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.WorkflowOutput

/** Sent when a chat's title is generated. */
@Serializable
@SerialName("chat.title")
data class ChatTitleUpdated(val chatId: KUUID, val title: String) : WorkflowOutput()
