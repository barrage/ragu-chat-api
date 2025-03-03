package net.barrage.llmao.app.workflow.chat

import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.MessageInsert
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.models.toMessage
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

private const val NANO_OFFSET = 20L

class ChatWorkflowRepository(private val dslContext: DSLContext) {
  suspend fun getChatWithMessages(id: KUUID): ChatWithMessages {
    val chat =
      dslContext
        .select(
          CHATS.ID,
          CHATS.USER_ID,
          CHATS.AGENT_ID,
          CHATS.TITLE,
          CHATS.CREATED_AT,
          CHATS.UPDATED_AT,
        )
        .from(CHATS)
        .where(CHATS.ID.eq(id))
        .awaitFirstOrNull()
        ?.into(CHATS)
        ?.toChat() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")

    val messages =
      dslContext
        .select(
          MESSAGES.ID,
          MESSAGES.SENDER,
          MESSAGES.SENDER_TYPE,
          MESSAGES.CONTENT,
          MESSAGES.CHAT_ID,
          MESSAGES.RESPONSE_TO,
          MESSAGES.CREATED_AT,
          MESSAGES.UPDATED_AT,
        )
        .from(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(id))
        .orderBy(MESSAGES.CREATED_AT.asc())
        .asFlow()
        .map { record -> record.into(MESSAGES).toMessage() }
        .toList()

    return ChatWithMessages(chat, messages)
  }

  suspend fun updateTitle(id: KUUID, userId: KUUID, title: String) {
    dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .awaitFirstOrNull() ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "Chat not found")
  }

  suspend fun insertMessagePair(userMessage: MessageInsert, assistantMessage: MessageInsert) {
    val now = KOffsetDateTime.now()
    dslContext.transactionCoroutine { ctx ->
      ctx
        .dsl()
        .insertInto(MESSAGES)
        .set(MESSAGES.ID, userMessage.id)
        .set(MESSAGES.CHAT_ID, userMessage.chatId)
        .set(MESSAGES.CONTENT, userMessage.content)
        .set(MESSAGES.SENDER, userMessage.sender)
        .set(MESSAGES.SENDER_TYPE, userMessage.senderType)
        .set(MESSAGES.CREATED_AT, now)
        .set(MESSAGES.UPDATED_AT, now)
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message pair")

      ctx
        .dsl()
        .insertInto(MESSAGES)
        .set(MESSAGES.ID, assistantMessage.id)
        .set(MESSAGES.CHAT_ID, assistantMessage.chatId)
        .set(MESSAGES.CONTENT, assistantMessage.content)
        .set(MESSAGES.SENDER, assistantMessage.sender)
        .set(MESSAGES.SENDER_TYPE, assistantMessage.senderType)
        .set(MESSAGES.RESPONSE_TO, userMessage.id)
        .set(MESSAGES.FINISH_REASON, assistantMessage.finishReason?.value)
        .set(MESSAGES.CREATED_AT, now.plusNanos(NANO_OFFSET))
        .set(MESSAGES.UPDATED_AT, now.plusNanos(NANO_OFFSET))
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message pair")
    }
  }

  suspend fun insertChat(
    chatId: KUUID,
    userId: KUUID,
    agentId: KUUID,
    userMessage: MessageInsert,
    assistantMessage: MessageInsert,
  ) {
    val now = KOffsetDateTime.now()
    return dslContext.transactionCoroutine { ctx ->
      ctx
        .dsl()
        .insertInto(CHATS)
        .set(CHATS.ID, chatId)
        .set(CHATS.USER_ID, userId)
        .set(CHATS.AGENT_ID, agentId)
        .set(CHATS.CREATED_AT, now)
        .set(CHATS.UPDATED_AT, now)
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert chat")

      ctx
        .dsl()
        .insertInto(MESSAGES)
        .set(MESSAGES.ID, userMessage.id)
        .set(MESSAGES.CHAT_ID, userMessage.chatId)
        .set(MESSAGES.CONTENT, userMessage.content)
        .set(MESSAGES.SENDER, userMessage.sender)
        .set(MESSAGES.SENDER_TYPE, userMessage.senderType)
        .set(MESSAGES.CREATED_AT, now)
        .set(MESSAGES.UPDATED_AT, now)
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message pair")

      ctx
        .dsl()
        .insertInto(MESSAGES)
        .set(MESSAGES.ID, assistantMessage.id)
        .set(MESSAGES.CHAT_ID, assistantMessage.chatId)
        .set(MESSAGES.CONTENT, assistantMessage.content)
        .set(MESSAGES.SENDER, assistantMessage.sender)
        .set(MESSAGES.SENDER_TYPE, assistantMessage.senderType)
        .set(MESSAGES.RESPONSE_TO, userMessage.id)
        .set(MESSAGES.FINISH_REASON, assistantMessage.finishReason?.value)
        .set(MESSAGES.CREATED_AT, now.plusNanos(NANO_OFFSET))
        .set(MESSAGES.UPDATED_AT, now.plusNanos(NANO_OFFSET))
        .awaitFirstOrNull() ?: throw AppError.internal("Failed to insert message pair")
    }
  }
}
