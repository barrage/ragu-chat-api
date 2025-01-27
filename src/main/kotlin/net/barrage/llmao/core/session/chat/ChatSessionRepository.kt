package net.barrage.llmao.core.session.chat

import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.models.toMessage
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
import org.jooq.DSLContext
import org.jooq.kotlin.coroutines.transactionCoroutine

class ChatSessionRepository(private val dslContext: DSLContext) {
  suspend fun getChatWithMessages(id: KUUID, historySize: Int): ChatWithMessages {
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
        .orderBy(MESSAGES.CREATED_AT.desc())
        .limit(historySize)
        .map { record -> record.into(MESSAGES).toMessage() }
        .toList()

    return ChatWithMessages(chat, messages)
  }

  suspend fun updateTitle(id: KUUID, userId: KUUID, title: String) {
    dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .awaitSingle()
  }

  suspend fun insertMessagePair(
    chatId: KUUID,
    userId: KUUID,
    prompt: String,
    agentConfigurationId: KUUID,
    response: String,
  ): KUUID {
    return dslContext.transactionCoroutine { ctx ->
      val messageId =
        ctx
          .dsl()
          .insertInto(MESSAGES)
          .set(MESSAGES.CHAT_ID, chatId)
          .set(MESSAGES.SENDER, userId)
          .set(MESSAGES.SENDER_TYPE, "user")
          .set(MESSAGES.CONTENT, prompt)
          .returning(MESSAGES.ID)
          .awaitSingle()
          .id

      ctx
        .dsl()
        .insertInto(MESSAGES)
        .set(MESSAGES.CHAT_ID, chatId)
        .set(MESSAGES.SENDER, agentConfigurationId)
        .set(MESSAGES.SENDER_TYPE, "assistant")
        .set(MESSAGES.CONTENT, response)
        .set(MESSAGES.RESPONSE_TO, messageId)
        .returning(MESSAGES.ID)
        .awaitSingle()
        .id ?: throw AppError.internal("Failed to insert message pair")
    }
  }

  suspend fun insertSystemMessage(id: KUUID, message: String) {
    dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER_TYPE, "system")
      .set(MESSAGES.CONTENT, message)
      .awaitSingle()
  }

  suspend fun insertChat(
    id: KUUID,
    userId: KUUID,
    prompt: String,
    agentId: KUUID,
    response: String,
  ): KUUID {
    return dslContext.transactionCoroutine { ctx ->
      val chatId =
        ctx
          .dsl()
          .insertInto(CHATS)
          .set(CHATS.ID, id)
          .set(CHATS.USER_ID, userId)
          .set(CHATS.AGENT_ID, agentId)
          .returning(CHATS.ID)
          .awaitSingle()
          .id ?: throw AppError.internal("Failed to insert chat")

      val messageId =
        ctx
          .dsl()
          .insertInto(MESSAGES)
          .set(MESSAGES.CHAT_ID, chatId)
          .set(MESSAGES.SENDER, userId)
          .set(MESSAGES.SENDER_TYPE, "user")
          .set(MESSAGES.CONTENT, prompt)
          .returning(MESSAGES.ID)
          .awaitSingle()
          .id ?: throw AppError.internal("Failed to insert user message")

      ctx
        .dsl()
        .insertInto(MESSAGES)
        .set(MESSAGES.CHAT_ID, chatId)
        .set(MESSAGES.SENDER, agentId)
        .set(MESSAGES.SENDER_TYPE, "assistant")
        .set(MESSAGES.CONTENT, response)
        .set(MESSAGES.RESPONSE_TO, messageId)
        .returning(MESSAGES.ID)
        .awaitSingle()
        .id ?: throw AppError.internal("Failed to insert assistant message")
    }
  }
}
