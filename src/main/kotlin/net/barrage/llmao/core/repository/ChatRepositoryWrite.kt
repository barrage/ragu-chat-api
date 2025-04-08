package net.barrage.llmao.core.repository

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.MessageInsert
import net.barrage.llmao.core.model.toChat
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_ATTACHMENTS
import net.barrage.llmao.tables.references.MESSAGE_GROUPS
import net.barrage.llmao.types.KUUID
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class ChatRepositoryWrite(private val dslContext: DSLContext, private val type: String) {
  suspend fun updateTitle(id: KUUID, userId: String, title: String) {
    dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .where(CHATS.TYPE.eq(type).and(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId))))
      .awaitFirstOrNull() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  suspend fun insertChatWithMessages(
    chatId: KUUID,
    userId: String,
    username: String?,
    agentId: KUUID,
    agentConfigurationId: KUUID,
    messages: List<MessageInsert>,
  ): KUUID =
    dslContext.transactionCoroutine { ctx ->
      ctx.dsl().insertChat(chatId, userId, username, agentId, type)
      ctx.dsl().insertMessages(chatId, agentConfigurationId, messages)
    }

  suspend fun insertChat(chatId: KUUID, userId: String, username: String?, agentId: KUUID): Chat =
    dslContext.insertChat(chatId, userId, username, agentId, type)

  suspend fun insertMessages(
    chatId: KUUID,
    agentConfigurationId: KUUID,
    messages: List<MessageInsert>,
  ): KUUID =
    dslContext.transactionCoroutine { ctx ->
      ctx.dsl().insertMessages(chatId, agentConfigurationId, messages)
    }
}

private suspend fun DSLContext.insertChat(
  chatId: KUUID,
  userId: String,
  username: String?,
  agentId: KUUID,
  type: String,
): Chat {
  return insertInto(CHATS)
    .set(CHATS.ID, chatId)
    .set(CHATS.USER_ID, userId)
    .set(CHATS.USERNAME, username)
    .set(CHATS.AGENT_ID, agentId)
    .set(CHATS.TYPE, type)
    .returning()
    .awaitSingle()
    .into(CHATS)
    .toChat()
}

private suspend fun DSLContext.insertMessages(
  chatId: KUUID,
  agentConfigurationId: KUUID,
  messages: List<MessageInsert>,
): KUUID {
  val messageGroupId =
    insertInto(MESSAGE_GROUPS)
      .set(MESSAGE_GROUPS.CHAT_ID, chatId)
      .set(MESSAGE_GROUPS.AGENT_CONFIGURATION_ID, agentConfigurationId)
      .returning(MESSAGE_GROUPS.ID)
      .awaitSingle()
      .id

  messages.forEachIndexed { index, message ->
    val messageId =
      insertInto(MESSAGES)
        .set(MESSAGES.ORDER, index)
        .set(MESSAGES.MESSAGE_GROUP_ID, messageGroupId)
        .set(MESSAGES.SENDER_TYPE, message.senderType)
        .set(MESSAGES.CONTENT, message.content)
        .set(MESSAGES.FINISH_REASON, message.finishReason?.value)
        .set(MESSAGES.TOOL_CALLS, message.toolCalls?.let { Json.encodeToString(it) })
        .set(MESSAGES.TOOL_CALL_ID, message.toolCallId)
        .returning(MESSAGES.ID)
        .awaitFirstOrNull()
        ?.id ?: throw AppError.internal("Failed to insert message")

    message.attachments?.forEach { attachment ->
      insertInto(MESSAGE_ATTACHMENTS)
        .set(MESSAGE_ATTACHMENTS.MESSAGE_ID, messageId)
        .set(MESSAGE_ATTACHMENTS.TYPE, attachment.type.name)
        .set(MESSAGE_ATTACHMENTS.PROVIDER, attachment.provider)
        .set(MESSAGE_ATTACHMENTS.ORDER, attachment.order)
        .set(MESSAGE_ATTACHMENTS.URL, attachment.url)
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert attachment")
    }
  }

  return messageGroupId as KUUID
}
