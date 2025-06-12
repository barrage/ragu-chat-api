package net.barrage.llmao.app.workflow.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.workflow.WorkflowOutput
import net.barrage.llmao.core.types.KUUID

/** Sent when a chat's title is generated. */
@Serializable
@SerialName("chat.title")
data class ChatTitleUpdated(val chatId: KUUID, val title: String) : WorkflowOutput()
