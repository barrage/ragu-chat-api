package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.FailedMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.models.toFailedMessage
import net.barrage.llmao.core.models.toMessage
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.FailedMessagesRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.FAILED_MESSAGES
import net.barrage.llmao.tables.references.MESSAGES
import org.jooq.SortField
import org.jooq.impl.DSL

class ChatRepository {
  fun getAll(pagination: PaginationSort, userId: KUUID? = null): CountedList<Chat> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(CHATS)
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .fetchOne(0, Int::class.java)

    val chats =
      dslContext
        .select()
        .from(CHATS)
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetchInto(ChatsRecord::class.java)
        .map { it.toChat() }

    return CountedList(total!!, chats)
  }

  fun get(id: KUUID): Chat? {
    return dslContext
      .select()
      .from(CHATS)
      .where(CHATS.ID.eq(id))
      .fetchOneInto(ChatsRecord::class.java)
      ?.toChat()
  }

  fun getWithMessages(id: KUUID): ChatWithMessages? {
    val chat = get(id) ?: return null
    val messages = getMessages(id)
    return ChatWithMessages(chat, messages)
  }

  fun getMessages(id: KUUID): List<Message> {
    return dslContext
      .select()
      .from(MESSAGES)
      .where(MESSAGES.CHAT_ID.eq(id))
      .fetchInto(MessagesRecord::class.java)
      .map { it.toMessage() }
  }

  fun getMessagesForUser(id: KUUID, userId: KUUID): List<Message> {
    return dslContext
      .select()
      .from(MESSAGES)
      .join(CHATS)
      .on(MESSAGES.CHAT_ID.eq(CHATS.ID))
      .where(MESSAGES.CHAT_ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .fetchInto(MessagesRecord::class.java)
      .map { it.toMessage() }
  }

  fun getMessage(chatId: KUUID, messageId: KUUID): Message? {
    return dslContext
      .selectFrom(MESSAGES)
      .where(MESSAGES.ID.eq(messageId).and(MESSAGES.CHAT_ID.eq(chatId)))
      .fetchOne(MessagesRecord::toMessage)
  }

  fun getMessageForUser(chatId: KUUID, messageId: KUUID, userId: KUUID): Message? {
    return dslContext
      .select()
      .from(MESSAGES)
      .join(CHATS)
      .on(MESSAGES.CHAT_ID.eq(CHATS.ID))
      .where(
        MESSAGES.ID.eq(messageId).and(MESSAGES.CHAT_ID.eq(chatId)).and(CHATS.USER_ID.eq(userId))
      )
      .fetchOne { it.into(MessagesRecord::class.java).toMessage() }
  }

  fun evaluateMessage(id: KUUID, evaluation: Boolean): Message? {
    return dslContext
      .update(MESSAGES)
      .set(MESSAGES.EVALUATION, evaluation)
      .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
      .where(MESSAGES.ID.eq(id))
      .returning()
      .fetchOne(MessagesRecord::toMessage)
  }

  fun evaluateMessage(id: KUUID, userId: KUUID, evaluation: Boolean): Message? {
    return dslContext
      .update(MESSAGES)
      .set(MESSAGES.EVALUATION, evaluation)
      .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
      .where(MESSAGES.ID.eq(id).and(MESSAGES.SENDER.eq(userId)))
      .returning()
      .fetchOne(MessagesRecord::toMessage)
  }

  fun updateTitle(id: KUUID, title: String): Chat? {
    return dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .set(CHATS.UPDATED_AT, OffsetDateTime.now())
      .where(CHATS.ID.eq(id))
      .returning()
      .fetchOne(ChatsRecord::toChat)
  }

  fun updateTitle(id: KUUID, userId: KUUID, title: String): Chat? {
    return dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .set(CHATS.UPDATED_AT, OffsetDateTime.now())
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .returning()
      .fetchOne(ChatsRecord::toChat)
  }

  fun insert(id: KUUID, userId: KUUID, agentId: KUUID, title: String?): Chat? {
    val chat =
      dslContext
        .insertInto(CHATS)
        .set(CHATS.ID, id)
        .set(CHATS.USER_ID, userId)
        .set(CHATS.AGENT_ID, agentId)
        .set(CHATS.TITLE, title)
        .returning()
        .fetchOne() ?: return null

    return Chat(
      id = chat.id!!,
      userId = chat.userId!!,
      agentId = chat.agentId!!,
      title = chat.title,
      createdAt = chat.createdAt!!,
      updatedAt = chat.updatedAt!!,
    )
  }

  fun insertFailedMessage(
    chatId: KUUID,
    userId: KUUID,
    content: String,
    failReason: String,
  ): FailedMessage? {
    return dslContext
      .insertInto(FAILED_MESSAGES)
      .set(FAILED_MESSAGES.CHAT_ID, chatId)
      .set(FAILED_MESSAGES.USER_ID, userId)
      .set(FAILED_MESSAGES.CONTENT, content)
      .set(FAILED_MESSAGES.FAIL_REASON, failReason)
      .returning()
      .fetchOne(FailedMessagesRecord::toFailedMessage)
  }

  fun insertUserMessage(id: KUUID, userId: KUUID, proompt: String): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER, userId)
      .set(MESSAGES.SENDER_TYPE, "user")
      .set(MESSAGES.CONTENT, proompt)
      .returning()
      .fetchOne(MessagesRecord::toMessage)!!
  }

  fun insertAssistantMessage(
    id: KUUID,
    agentId: KUUID,
    response: String,
    messageId: KUUID,
  ): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER, agentId)
      .set(MESSAGES.SENDER_TYPE, "assistant")
      .set(MESSAGES.CONTENT, response)
      .set(MESSAGES.RESPONSE_TO, messageId)
      .returning()
      .fetchOne(MessagesRecord::toMessage)!!
  }

  fun insertSystemMessage(id: KUUID, message: String): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER_TYPE, "system")
      .set(MESSAGES.CONTENT, message)
      .returning()
      .fetchOne(MessagesRecord::toMessage)!!
  }

  fun delete(id: KUUID): Int {
    return dslContext.deleteFrom(CHATS).where(CHATS.ID.eq(id)).execute()
  }

  fun delete(id: KUUID, userId: KUUID): Int {
    return dslContext
      .deleteFrom(CHATS)
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .execute()
  }

  private fun getSortOrder(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "createdAt" -> CHATS.CREATED_AT
        "updatedAt" -> CHATS.UPDATED_AT
        "agentId" -> CHATS.AGENT_ID
        "title" -> CHATS.TITLE
        else -> CHATS.CREATED_AT
      }

    val order =
      if (sortOrder == SortOrder.DESC) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return order
  }
}
