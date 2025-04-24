package net.barrage.llmao.app.workflow.chat.model

import net.barrage.llmao.core.model.MessageGroup
import net.barrage.llmao.tables.records.MessageGroupsRecord
import net.barrage.llmao.types.KOffsetDateTime
import net.barrage.llmao.types.KUUID

class ChatMessageGroup(
  id: KUUID,
  chatId: KUUID,
  createdAt: KOffsetDateTime,
  updatedAt: KOffsetDateTime,
  /** The agent configuration at the time of interaction. */
  val agentConfigurationId: KUUID,
) : MessageGroup(id, chatId, createdAt, updatedAt)

fun MessageGroupsRecord.toChatMessageGroup() =
  ChatMessageGroup(
    id = this.id!!,
    chatId = this.chatId,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    agentConfigurationId = this.agentConfigurationId,
  )
