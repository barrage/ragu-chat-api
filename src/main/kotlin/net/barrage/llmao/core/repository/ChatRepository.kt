package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import net.barrage.llmao.core.models.AgentConfigurationEvaluatedMessageCounts
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatCounts
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.FailedMessage
import net.barrage.llmao.core.models.GraphData
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.Period
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toAgent
import net.barrage.llmao.core.models.toChat
import net.barrage.llmao.core.models.toFailedMessage
import net.barrage.llmao.core.models.toMessage
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.ChatsRecord
import net.barrage.llmao.tables.records.FailedMessagesRecord
import net.barrage.llmao.tables.records.MessagesRecord
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.FAILED_MESSAGES
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class ChatRepository(private val dslContext: DSLContext) {
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

  fun getAllAdmin(
    pagination: PaginationSort,
    userId: KUUID? = null,
  ): CountedList<ChatWithUserAndAgent> {
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
        .leftJoin(AGENTS)
        .on(CHATS.AGENT_ID.eq(AGENTS.ID))
        .leftJoin(USERS)
        .on(CHATS.USER_ID.eq(USERS.ID))
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .map {
          ChatWithUserAndAgent(
            it.into(CHATS).toChat(),
            it.into(USERS).toUser(),
            it.into(AGENTS).toAgent(),
          )
        }

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

  fun getMessages(chatId: KUUID): List<Message> {
    return dslContext
      .select()
      .from(MESSAGES)
      .where(MESSAGES.CHAT_ID.eq(chatId))
      .orderBy(MESSAGES.CREATED_AT.desc())
      .fetchInto(MessagesRecord::class.java)
      .map { it.toMessage() }
  }

  fun getMessagesForUser(chatId: KUUID, userId: KUUID): List<Message> {
    return dslContext
      .select()
      .from(MESSAGES)
      .join(CHATS)
      .on(MESSAGES.CHAT_ID.eq(CHATS.ID))
      .where(MESSAGES.CHAT_ID.eq(chatId).and(CHATS.USER_ID.eq(userId)))
      .orderBy(MESSAGES.CREATED_AT.desc())
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
      userId = chat.userId,
      agentId = chat.agentId,
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
    agentConfigurationId: KUUID,
    response: String,
    messageId: KUUID,
  ): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER, agentConfigurationId)
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

  fun getChatCounts(): ChatCounts {
    return ChatCounts(
      dslContext.selectCount().from(CHATS).fetchOne(0, Int::class.java)!!,
      dslContext
        .select(CHATS.AGENT_ID, AGENTS.NAME, DSL.count())
        .from(CHATS)
        .join(AGENTS)
        .on(CHATS.AGENT_ID.eq(AGENTS.ID))
        .groupBy(CHATS.AGENT_ID, AGENTS.NAME)
        .fetch()
        .map { GraphData(it.value2()!!, it.value3()!!) },
    )
  }

  fun agentsChatHistoryCounts(period: Period): Map<String, Map<String, Int>> {
    // start date based on period
    val startDate =
      when (period) {
        Period.WEEK -> KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(7)
        Period.MONTH -> KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusMonths(1)
        Period.YEAR ->
          KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusYears(1).withDayOfMonth(1)
      }

    // Generate a subquery that counts the number of chats for each agent and date.
    val chatDateCount =
      dslContext
        .select(
          CHATS.AGENT_ID.`as`("agent_id"), // Agent ID
          DSL.trunc(CHATS.CREATED_AT, period.datePart).`as`("date"), // Date
          DSL.count().`as`("count"), // Count
        )
        .from(CHATS)
        .where(CHATS.CREATED_AT.ge(startDate))
        .groupBy(CHATS.AGENT_ID, DSL.trunc(CHATS.CREATED_AT, period.datePart))
        .asTable("chat_date_counts")

    // Join the date series table with the recent chats subquery to get the count for each date.
    return dslContext
      .select(
        AGENTS.ID,
        AGENTS.NAME, // Agent name
        chatDateCount.field("date", KOffsetDateTime::class.java), // Date
        DSL.coalesce(chatDateCount.field("count", Int::class.java), 0).`as`("count"), // Count
      )
      .from(AGENTS)
      .leftJoin(chatDateCount)
      .on(AGENTS.ID.eq(chatDateCount.field("agent_id", UUID::class.java)))
      .where(AGENTS.ACTIVE.isTrue)
      .orderBy(AGENTS.NAME.asc(), chatDateCount.field("date", KOffsetDateTime::class.java)!!.asc())
      .fetch()
      .groupBy { it.value2()!! }
      .map { agent ->
        Pair(
          agent.key,
          agent.value.associate { Pair(it.value3()?.toLocalDate().toString(), it.value4()) },
        )
      }
      .toMap()
  }

  fun getAgentConfigurationMessageCounts(
    versionId: KUUID
  ): AgentConfigurationEvaluatedMessageCounts {
    val total =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .where(MESSAGES.SENDER.eq(versionId))
        .fetchOne(0, Int::class.java)!!

    val positive =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .where(MESSAGES.SENDER.eq(versionId).and(MESSAGES.EVALUATION.isTrue))
        .fetchOne(0, Int::class.java)!!

    val negative = total - positive

    return AgentConfigurationEvaluatedMessageCounts(total, positive, negative)
  }

  /** List all evaluated messages for a given agent configuration version. */
  fun getAgentConfigurationEvaluatedMessages(
    versionId: KUUID,
    evaluation: Boolean? = null,
    pagination: PaginationSort,
  ): CountedList<Message> {
    val order = getSortOrderEvaluatedMessages(pagination)
    val (limit, offset) = pagination.limitOffset()

    val count =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .where(
          MESSAGES.SENDER.eq(versionId)
            .and(MESSAGES.EVALUATION.isNotNull)
            .and(
              if (evaluation == null) {
                DSL.noCondition()
              } else {
                MESSAGES.EVALUATION.eq(evaluation)
              }
            )
        )
        .fetchOne(0, Int::class.java)!!

    val messages =
      dslContext
        .selectFrom(MESSAGES)
        .where(
          MESSAGES.SENDER.eq(versionId)
            .and(MESSAGES.EVALUATION.isNotNull)
            .and(
              if (evaluation == null) {
                DSL.noCondition()
              } else {
                MESSAGES.EVALUATION.eq(evaluation)
              }
            )
        )
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch(MessagesRecord::toMessage)

    return CountedList(count, messages)
  }

  private fun getSortOrderEvaluatedMessages(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "createdAt" -> MESSAGES.CREATED_AT
        "updatedAt" -> MESSAGES.UPDATED_AT
        "evaluation" -> MESSAGES.EVALUATION
        else -> MESSAGES.CREATED_AT
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
