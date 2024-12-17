package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.future.await
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.models.AgentChatsOnDate
import net.barrage.llmao.core.models.AgentConfigurationEvaluatedMessageCounts
import net.barrage.llmao.core.models.Chat
import net.barrage.llmao.core.models.ChatCount
import net.barrage.llmao.core.models.ChatCounts
import net.barrage.llmao.core.models.ChatWithMessages
import net.barrage.llmao.core.models.ChatWithUserAndAgent
import net.barrage.llmao.core.models.FailedMessage
import net.barrage.llmao.core.models.Message
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.Pagination
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
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.FAILED_MESSAGES
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext
import org.jooq.SortField
import org.jooq.impl.DSL

class ChatRepository(private val dslContext: DSLContext) {
  suspend fun getAll(pagination: PaginationSort, userId: KUUID? = null): CountedList<Chat> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(CHATS)
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .awaitSingle()
        .value1()
        ?.toInt() ?: 0

    val chats =
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
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetchAsync()
        .await()
        .map { it.into(CHATS).toChat() }

    return CountedList(total, chats)
  }

  suspend fun getAllAdmin(
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
        .awaitSingle()
        .value1()
        ?.toInt() ?: 0

    val chats =
      dslContext
        .select(
          CHATS.ID,
          CHATS.USER_ID,
          CHATS.AGENT_ID,
          CHATS.TITLE,
          CHATS.CREATED_AT,
          CHATS.UPDATED_AT,
          AGENTS.ID,
          AGENTS.NAME,
          AGENTS.DESCRIPTION,
          AGENTS.ACTIVE,
          AGENTS.ACTIVE_CONFIGURATION_ID,
          AGENTS.LANGUAGE,
          AGENTS.CREATED_AT,
          AGENTS.UPDATED_AT,
          USERS.ID,
          USERS.EMAIL,
          USERS.FULL_NAME,
          USERS.FIRST_NAME,
          USERS.LAST_NAME,
          USERS.ACTIVE,
          USERS.ROLE,
          USERS.CREATED_AT,
          USERS.UPDATED_AT,
        )
        .from(CHATS)
        .leftJoin(AGENTS)
        .on(CHATS.AGENT_ID.eq(AGENTS.ID))
        .leftJoin(USERS)
        .on(CHATS.USER_ID.eq(USERS.ID))
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetchAsync()
        .await()
        .map {
          ChatWithUserAndAgent(
            it.into(CHATS).toChat(),
            it.into(USERS).toUser(),
            it.into(AGENTS).toAgent(),
          )
        }

    return CountedList(total, chats)
  }

  suspend fun get(id: KUUID): Chat? {
    return dslContext
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
      ?.toChat()
  }

  suspend fun getWithMessages(id: KUUID, pagination: Pagination): ChatWithMessages? {
    val chat = get(id) ?: return null
    val messages = getMessages(id, pagination)
    return ChatWithMessages(chat, messages.items)
  }

  suspend fun getMessages(chatId: KUUID, pagination: Pagination): CountedList<Message> {
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(chatId))
        .awaitSingle()
        .value1() ?: 0

    val messages =
      dslContext
        .select(
          MESSAGES.ID,
          MESSAGES.SENDER,
          MESSAGES.SENDER_TYPE,
          MESSAGES.CONTENT,
          MESSAGES.CHAT_ID,
          MESSAGES.RESPONSE_TO,
          MESSAGES.EVALUATION,
          MESSAGES.CREATED_AT,
          MESSAGES.UPDATED_AT,
        )
        .from(MESSAGES)
        .where(MESSAGES.CHAT_ID.eq(chatId))
        .orderBy(MESSAGES.CREATED_AT.desc())
        .limit(limit)
        .offset(offset)
        .fetchAsync()
        .await()
        .map { it.into(MESSAGES).toMessage() }

    return CountedList(total, messages)
  }

  suspend fun getMessagesForUser(
    chatId: KUUID,
    userId: KUUID,
    pagination: Pagination,
  ): CountedList<Message> {
    val (limit, offset) = pagination.limitOffset()

    val total =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .join(CHATS)
        .on(MESSAGES.CHAT_ID.eq(CHATS.ID))
        .where(MESSAGES.CHAT_ID.eq(chatId).and(CHATS.USER_ID.eq(userId)))
        .awaitSingle()
        .value1() ?: 0

    val messages =
      dslContext
        .select(
          MESSAGES.ID,
          MESSAGES.SENDER,
          MESSAGES.SENDER_TYPE,
          MESSAGES.CONTENT,
          MESSAGES.CHAT_ID,
          MESSAGES.RESPONSE_TO,
          MESSAGES.EVALUATION,
          MESSAGES.CREATED_AT,
          MESSAGES.UPDATED_AT,
        )
        .from(MESSAGES)
        .join(CHATS)
        .on(MESSAGES.CHAT_ID.eq(CHATS.ID))
        .where(MESSAGES.CHAT_ID.eq(chatId).and(CHATS.USER_ID.eq(userId)))
        .orderBy(MESSAGES.CREATED_AT.desc())
        .limit(limit)
        .offset(offset)
        .fetchAsync()
        .await()
        .map { it.into(MESSAGES).toMessage() }

    return CountedList(total, messages)
  }

  suspend fun getMessage(chatId: KUUID, messageId: KUUID): Message? {
    return dslContext
      .select(
        MESSAGES.ID,
        MESSAGES.SENDER,
        MESSAGES.SENDER_TYPE,
        MESSAGES.CONTENT,
        MESSAGES.CHAT_ID,
        MESSAGES.RESPONSE_TO,
        MESSAGES.EVALUATION,
        MESSAGES.CREATED_AT,
        MESSAGES.UPDATED_AT,
      )
      .from(MESSAGES)
      .where(MESSAGES.ID.eq(messageId).and(MESSAGES.CHAT_ID.eq(chatId)))
      .awaitFirstOrNull()
      ?.into(MESSAGES)
      ?.toMessage()
  }

  suspend fun evaluateMessage(id: KUUID, evaluation: Boolean): Message? {
    return dslContext
      .update(MESSAGES)
      .set(MESSAGES.EVALUATION, evaluation)
      .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
      .where(MESSAGES.ID.eq(id))
      .returning()
      .awaitFirstOrNull()
      ?.into(MESSAGES)
      ?.toMessage()
  }

  suspend fun evaluateMessage(id: KUUID, userId: KUUID, evaluation: Boolean): Message? {
    return dslContext
      .update(MESSAGES)
      .set(MESSAGES.EVALUATION, evaluation)
      .set(MESSAGES.UPDATED_AT, OffsetDateTime.now())
      .where(MESSAGES.ID.eq(id).and(MESSAGES.SENDER.eq(userId)))
      .returning()
      .awaitFirstOrNull()
      ?.into(MESSAGES)
      ?.toMessage()
  }

  suspend fun updateTitle(id: KUUID, title: String): Chat? {
    return dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .set(CHATS.UPDATED_AT, OffsetDateTime.now())
      .where(CHATS.ID.eq(id))
      .returning()
      .awaitFirstOrNull()
      ?.into(CHATS)
      ?.toChat()
  }

  suspend fun updateTitle(id: KUUID, userId: KUUID, title: String): Chat? {
    return dslContext
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .set(CHATS.UPDATED_AT, OffsetDateTime.now())
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .returning()
      .awaitFirstOrNull()
      ?.into(CHATS)
      ?.toChat()
  }

  suspend fun insert(id: KUUID, userId: KUUID, agentId: KUUID, title: String?): Chat? {
    return dslContext
      .insertInto(CHATS)
      .set(CHATS.ID, id)
      .set(CHATS.USER_ID, userId)
      .set(CHATS.AGENT_ID, agentId)
      .set(CHATS.TITLE, title)
      .returning()
      .awaitFirstOrNull()
      ?.into(CHATS)
      ?.toChat()
  }

  suspend fun insertFailedMessage(
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
      .awaitSingle()
      ?.into(FAILED_MESSAGES)
      ?.toFailedMessage()
  }

  suspend fun insertUserMessage(id: KUUID, userId: KUUID, proompt: String): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER, userId)
      .set(MESSAGES.SENDER_TYPE, "user")
      .set(MESSAGES.CONTENT, proompt)
      .returning()
      .awaitSingle()
      ?.into(MESSAGES)
      ?.toMessage()!!
  }

  suspend fun insertAssistantMessage(
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
      .awaitSingle()
      ?.into(MESSAGES)
      ?.toMessage()!!
  }

  suspend fun insertSystemMessage(id: KUUID, message: String): Message {
    return dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.CHAT_ID, id)
      .set(MESSAGES.SENDER_TYPE, "system")
      .set(MESSAGES.CONTENT, message)
      .returning()
      .awaitSingle()
      ?.into(MESSAGES)
      ?.toMessage()!!
  }

  suspend fun delete(id: KUUID): Int {
    return dslContext.deleteFrom(CHATS).where(CHATS.ID.eq(id)).awaitSingle()
  }

  suspend fun delete(id: KUUID, userId: KUUID): Int {
    return dslContext
      .deleteFrom(CHATS)
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .awaitSingle()
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

  suspend fun getChatCounts(): ChatCounts {
    val total = dslContext.selectCount().from(CHATS).awaitSingle().value1() ?: 0

    val rows =
      dslContext
        .select(CHATS.AGENT_ID, DSL.count(), AGENTS.NAME)
        .from(CHATS)
        .join(AGENTS)
        .on(CHATS.AGENT_ID.eq(AGENTS.ID))
        .groupBy(CHATS.AGENT_ID, AGENTS.NAME)
        .fetchAsync()
        .await()

    val chats = mutableListOf<ChatCount>()

    for (row in rows) {
      // Safe to yell because of non-null constraints
      val id = row.value1()!!
      val count = row.value2()
      val name = row.value3()!!
      chats.add(ChatCount(id, name, count))
    }

    return ChatCounts(total, chats)
  }

  /**
   * Returns a map of agent names to a map of dates to chat counts for the given period. The map is
   * { Agent -> { Date -> Count }}.
   */
  suspend fun agentsChatHistoryCounts(period: Period): List<AgentChatsOnDate> {
    val startDate =
      when (period) {
        Period.WEEK -> KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusDays(6)
        Period.MONTH -> KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusMonths(1)
        Period.YEAR ->
          KOffsetDateTime.now().truncatedTo(ChronoUnit.DAYS).minusYears(1).withDayOfMonth(1)
      }

    return dslContext
      .resultQuery(
        """
          WITH chat_date_count AS (
              SELECT 
                  agent_id,
                  DATE_TRUNC('${period.datePart}', created_at) AS date,
                  COUNT(*) AS count
              FROM chats 
              WHERE chats.created_at >= '$startDate'
              GROUP BY agent_id, date
              ORDER BY date
          ) 
          SELECT 
              agents.id,
              agents.name,
              chat_date_count.date,
              COALESCE(chat_date_count.count, 0) AS count
          FROM agents
          LEFT JOIN chat_date_count ON agents.id = chat_date_count.agent_id
          ORDER BY chat_date_count.agent_id, chat_date_count.date
        """
      )
      .fetchAsync()
      .await()
      .map {
        AgentChatsOnDate(
          agentId = it.get("id", KUUID::class.java),
          agentName = it.get("name", String::class.java),
          // If no chats for a given date, this will be null and we can skip the whole entry
          date = it.get("date", KOffsetDateTime::class.java)?.toLocalDate(),
          amount = it.get("count", Long::class.java),
        )
      }
      .filterNotNull()
  }

  suspend fun getAgentConfigurationMessageCounts(
    versionId: KUUID
  ): AgentConfigurationEvaluatedMessageCounts {
    val total =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .where(MESSAGES.SENDER.eq(versionId))
        .awaitSingle()
        .value1() ?: 0

    val positive =
      dslContext
        .selectCount()
        .from(MESSAGES)
        .where(MESSAGES.SENDER.eq(versionId).and(MESSAGES.EVALUATION.isTrue))
        .awaitSingle()
        .value1() ?: 0

    val negative = total - positive

    return AgentConfigurationEvaluatedMessageCounts(total, positive, negative)
  }

  /** List all evaluated messages for a given agent configuration version. */
  suspend fun getAgentConfigurationEvaluatedMessages(
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
        .awaitSingle()
        .value1() ?: 0

    val messages =
      dslContext
        .select(
          MESSAGES.ID,
          MESSAGES.SENDER,
          MESSAGES.SENDER_TYPE,
          MESSAGES.CONTENT,
          MESSAGES.CHAT_ID,
          MESSAGES.RESPONSE_TO,
          MESSAGES.EVALUATION,
          MESSAGES.CREATED_AT,
          MESSAGES.UPDATED_AT,
        )
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
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetchAsync()
        .await()
        .map { it.into(MESSAGES).toMessage() }

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
