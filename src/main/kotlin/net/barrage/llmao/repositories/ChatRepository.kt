package net.barrage.llmao.repositories

import java.time.OffsetDateTime
import net.barrage.llmao.error.apiError
import net.barrage.llmao.error.internalError
import net.barrage.llmao.models.Chat
import net.barrage.llmao.models.ChatFull
import net.barrage.llmao.models.ChatWithConfig
import net.barrage.llmao.models.FailedMessage
import net.barrage.llmao.models.Message
import net.barrage.llmao.models.toChat
import net.barrage.llmao.models.toChatWithConfig
import net.barrage.llmao.models.toFailedMessage
import net.barrage.llmao.models.toLlmConfig
import net.barrage.llmao.models.toMessage
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.plugins.transaction
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.FailedMessagesRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.references.*

class ChatRepository {
  fun getAll(
    offset: Int,
    size: Int,
    sortBy: String,
    sortOrder: String,
    userId: KUUID? = null,
  ): List<ChatDTO> {
    val sortField =
      when (sortBy) {
        "createdAt" -> CHATS.CREATED_AT
        "updatedAt" -> CHATS.UPDATED_AT
        "agentId" -> CHATS.AGENT_ID
        "model" -> LLM_CONFIGS.MODEL
        else -> CHATS.CREATED_AT
      }

    val orderField =
      if (sortOrder.equals("desc", ignoreCase = true)) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    val selectQuery =
      dslContext
        .select()
        .from(CHATS)
        .leftJoin(LLM_CONFIGS)
        .on(CHATS.ID.eq(LLM_CONFIGS.CHAT_ID))
        .leftJoin(MESSAGES)
        .on(CHATS.ID.eq(MESSAGES.CHAT_ID))

    if (userId != null) {
      selectQuery.where(CHATS.USER_ID.eq(userId))
    }

    return selectQuery
      .orderBy(orderField)
      .limit(size)
      .offset(offset)
      .fetch()
      .groupBy { it[CHATS.ID] }
      .map { toChatDTO(it.value) }
  }

  fun countAll(userId: KUUID? = null): Int {
    val selectQuery = dslContext.selectCount().from(CHATS)

    if (userId != null) {
      selectQuery.where(CHATS.USER_ID.eq(userId))
    }

    return selectQuery.fetchOne(0, Int::class.java)!!
  }

  fun get(id: KUUID): ChatDTO {
    return dslContext
      .select()
      .from(CHATS)
      .leftJoin(LLM_CONFIGS)
      .on(CHATS.ID.eq(LLM_CONFIGS.CHAT_ID))
      .where(CHATS.ID.eq(id))
      .fetchOne()
      ?.toChatWithConfig()
  }

  fun getWithMessages(id: KUUID): ChatFull? {
    val chat = get(id) ?: return null
    val messages = getMessages(id)
    return ChatFull(chat, messages)
  }

  fun getAllForUser(userId: KUUID): List<Chat> {
    return dslContext
      .select()
      .from(CHATS)
      .where(CHATS.USER_ID.eq(userId))
      .fetchInto(ChatsRecord::class.java)
      .map { it.toChat() }
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
      .where(MESSAGES.ID.eq(id).and(MESSAGES.SENDER.eq(userId.toString())))
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

  fun insertWithConfig(
    id: KUUID,
    userId: KUUID,
    agentId: Int,
    title: String?,
    model: String,
    temperature: Double,
    language: String,
    provider: String,
  ): ChatWithConfig {

    val (chat, config) =
      transaction { config ->
        val insertedChat =
          config
            .dsl()
            .insertInto(CHATS)
            .set(CHATS.ID, id)
            .set(CHATS.USER_ID, userId)
            .set(CHATS.AGENT_ID, agentId)
            .set(CHATS.TITLE, title)
            .returning()
            .fetchOne()

        val insertedLLMConfig =
          config
            .dsl()
            .insertInto(LLM_CONFIGS)
            .set(LLM_CONFIGS.CHAT_ID, id)
            .set(LLM_CONFIGS.MODEL, model)
            .set(LLM_CONFIGS.LANGUAGE, language)
            .set(LLM_CONFIGS.TEMPERATURE, temperature)
            .set(LLM_CONFIGS.PROVIDER, provider)
            .returning()
            .fetchOne()

        return@transaction Pair(insertedChat, insertedLLMConfig)
      }

    if (chat == null || config == null) {
      // TODO: Figure out
      throw internalError()
    }

    return ChatWithConfig(
      chat =
        Chat(
          id = chat.id!!,
          userId = chat.userId!!,
          agentId = chat.agentId!!,
          title = chat.title,
          createdAt = chat.createdAt!!,
          updatedAt = chat.updatedAt!!,
        ),
      config = config.toLlmConfig(),
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
      .set(MESSAGES.SENDER, userId.toString())
      .set(MESSAGES.SENDER_TYPE, "user")
      .set(MESSAGES.CONTENT, proompt)
      .returning()
      .fetchOne(MessagesRecord::toMessage)!!
  }

  fun insertAssistantMessage(id: KUUID, agentId: Int, response: String, messageId: KUUID): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER, agentId.toString())
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

  fun getChatWithConfig(id: KUUID): ChatWithConfig {
    val record =
      dslContext
        .select()
        .from(CHATS)
        .leftJoin(LLM_CONFIGS)
        .on(CHATS.ID.eq(LLM_CONFIGS.CHAT_ID))
        .where(CHATS.ID.eq(id))
        .fetchOne()

    if (record == null) {
      throw apiError("Entity does not exist", "Chat with ID '$id'")
    }

    return record.toChatWithConfig()
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
}
