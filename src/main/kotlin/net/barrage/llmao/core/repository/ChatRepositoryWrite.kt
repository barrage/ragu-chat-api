package net.barrage.llmao.core.repository

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.MessageInsert
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_GROUPS
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

  suspend fun insertChat(chatId: KUUID, userId: String, username: String?, agentId: KUUID): Chat {
    return dslContext
      .insertInto(CHATS)
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

  suspend fun insertMessages(
    chatId: KUUID,
    agentConfigurationId: KUUID,
    messages: List<MessageInsert>,
  ): KUUID {
    return dslContext.transactionCoroutine { ctx ->
      val messageGroupId =
        ctx
          .dsl()
          .insertInto(MESSAGE_GROUPS)
          .set(MESSAGE_GROUPS.CHAT_ID, chatId)
          .set(MESSAGE_GROUPS.AGENT_CONFIGURATION_ID, agentConfigurationId)
          .returning(MESSAGE_GROUPS.ID)
          .awaitSingle()
          .id

      messages.forEachIndexed { index, message ->
        ctx
          .dsl()
          .insertInto(MESSAGES)
          .set(MESSAGES.ORDER, index)
          .set(MESSAGES.MESSAGE_GROUP_ID, messageGroupId)
          .set(MESSAGES.SENDER_TYPE, message.senderType)
          .set(MESSAGES.CONTENT, message.content)
          .set(MESSAGES.FINISH_REASON, message.finishReason?.value)
          .set(MESSAGES.TOOL_CALLS, Json.encodeToString(message.toolCalls))
          .set(MESSAGES.TOOL_CALL_ID, message.toolCallId)
          .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message")
      }

      messageGroupId as KUUID
    }
  }
}
