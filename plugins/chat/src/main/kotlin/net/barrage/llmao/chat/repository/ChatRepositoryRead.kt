package net.barrage.llmao.chat.repository

import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.chat.model.AgentChatsOnDate
import net.barrage.llmao.chat.model.AgentConfigurationEvaluatedMessageCounts
import net.barrage.llmao.chat.model.Chat
import net.barrage.llmao.chat.model.ChatCount
import net.barrage.llmao.chat.model.ChatCounts
import net.barrage.llmao.chat.model.ChatWithAgent
import net.barrage.llmao.chat.model.ChatWithMessages
import net.barrage.llmao.chat.model.SearchFiltersAdminChats
import net.barrage.llmao.chat.model.toAgent
import net.barrage.llmao.chat.model.toChat
import net.barrage.llmao.chat.tables.references.AGENTS
import net.barrage.llmao.chat.tables.references.CHATS
import net.barrage.llmao.core.database.set
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.Pagination
import net.barrage.llmao.core.model.common.PaginationSort
import net.barrage.llmao.core.model.common.Period
import net.barrage.llmao.core.model.common.SortOrder
import net.barrage.llmao.core.model.toMessage
import net.barrage.llmao.core.model.toMessageGroup
import net.barrage.llmao.core.model.toMessageGroupEvaluation
import net.barrage.llmao.core.repository.MessageRepository
import net.barrage.llmao.core.tables.records.MessageGroupEvaluationsRecord
import net.barrage.llmao.core.tables.references.MESSAGES
import net.barrage.llmao.core.tables.references.MESSAGE_GROUPS
import net.barrage.llmao.core.tables.references.MESSAGE_GROUP_EVALUATIONS
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.InsertOnDuplicateSetMoreStep
import org.jooq.SortField
import org.jooq.impl.DSL

class ChatRepositoryRead(override val dsl: DSLContext, private val type: String) :
  MessageRepository {
  suspend fun getAll(pagination: PaginationSort, userId: String? = null): CountedList<Chat> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total =
      dsl
        .selectCount()
        .from(CHATS)
        .where(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        .awaitSingle()
        .value1() ?: 0

    val chats =
      dsl
        .select(
          CHATS.ID,
          CHATS.USER_ID,
          CHATS.USERNAME,
          CHATS.AGENT_ID,
          CHATS.AGENT_CONFIGURATION_ID,
          CHATS.TITLE,
          CHATS.TYPE,
          CHATS.CREATED_AT,
          CHATS.UPDATED_AT,
        )
        .from(CHATS)
        .where(
          CHATS.TYPE.eq(type).and(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
        )
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { it.into(CHATS).toChat() }
        .toList()

    return CountedList(total, chats)
  }

  suspend fun getAllAdmin(
    pagination: PaginationSort,
    filters: SearchFiltersAdminChats,
  ): CountedList<ChatWithAgent> {
    val order = getSortOrder(pagination)
    val (limit, offset) = pagination.limitOffset()

    val conditions = filters.toConditions()
    val total = dsl.selectCount().from(CHATS).where(conditions).awaitSingle().value1()?.toInt() ?: 0

    val chats =
      dsl
        .select(
          CHATS.ID,
          CHATS.USER_ID,
          CHATS.USERNAME,
          CHATS.AGENT_ID,
          CHATS.AGENT_CONFIGURATION_ID,
          CHATS.TITLE,
          CHATS.TYPE,
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
        )
        .from(CHATS)
        .leftJoin(AGENTS)
        .on(CHATS.AGENT_ID.eq(AGENTS.ID))
        .where(CHATS.TYPE.eq(type).and(conditions))
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { ChatWithAgent(it.into(CHATS).toChat(), it.into(AGENTS).toAgent()) }
        .toList()

    return CountedList(total, chats)
  }

  suspend fun getSingleByUserId(userId: String, messageLimit: Int = 50): ChatWithMessages? {
    val chat =
      dsl
        .select(
          CHATS.ID,
          CHATS.USER_ID,
          CHATS.AGENT_ID,
          CHATS.AGENT_CONFIGURATION_ID,
          CHATS.TITLE,
          CHATS.TYPE,
          CHATS.CREATED_AT,
          CHATS.UPDATED_AT,
        )
        .from(CHATS)
        .where(CHATS.TYPE.eq(type).and(CHATS.USER_ID.eq(userId)))
        .orderBy(CHATS.CREATED_AT.desc())
        .limit(1)
        .awaitFirstOrNull()
        ?.into(CHATS) ?: return null

    val messages = getWorkflowMessages(chat.id!!, Pagination(1, messageLimit))

    return ChatWithMessages(chat.into(CHATS).toChat(), messages)
  }

  suspend fun get(id: KUUID, userId: String? = null): Chat? {
    return dsl
      .select(
        CHATS.ID,
        CHATS.USER_ID,
        CHATS.USERNAME,
        CHATS.AGENT_ID,
        CHATS.AGENT_CONFIGURATION_ID,
        CHATS.TITLE,
        CHATS.TYPE,
        CHATS.CREATED_AT,
        CHATS.UPDATED_AT,
      )
      .from(CHATS)
      .where(
        CHATS.TYPE.eq(type)
          .and(CHATS.ID.eq(id))
          .and(userId?.let { CHATS.USER_ID.eq(userId) } ?: DSL.noCondition())
      )
      .awaitFirstOrNull()
      ?.into(CHATS)
      ?.toChat()
  }

  suspend fun getWithMessages(
    id: KUUID,
    pagination: Pagination,
    userId: String? = null,
  ): ChatWithMessages? {
    val chat = get(id, userId) ?: return null
    val messages = getWorkflowMessages(id, pagination)
    return ChatWithMessages(chat, messages)
  }

  suspend fun evaluateMessageGroup(messageGroupId: KUUID, input: EvaluateMessage): Int {
    if (input.evaluation == null) {
      return dsl
        .deleteFrom(MESSAGE_GROUP_EVALUATIONS)
        .where(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID.eq(messageGroupId))
        .awaitSingle()
    }

    return dsl
      .insertInto(MESSAGE_GROUP_EVALUATIONS)
      .set(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID, messageGroupId)
      .set(MESSAGE_GROUP_EVALUATIONS.EVALUATION, input.evaluation)
      .set(MESSAGE_GROUP_EVALUATIONS.FEEDBACK, input.feedback.value())
      .onConflict(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID)
      .doUpdate()
      .let { input.applyUpdates(it as InsertOnDuplicateSetMoreStep<MessageGroupEvaluationsRecord>) }
      .awaitSingle()
  }

  suspend fun updateTitle(id: KUUID, title: String): Chat? {
    return dsl
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .set(CHATS.UPDATED_AT, OffsetDateTime.now())
      .where(CHATS.ID.eq(id))
      .returning()
      .awaitFirstOrNull()
      ?.into(CHATS)
      ?.toChat()
  }

  suspend fun userUpdateTitle(id: KUUID, userId: String, title: String): Chat? {
    return dsl
      .update(CHATS)
      .set(CHATS.TITLE, title)
      .set(CHATS.UPDATED_AT, OffsetDateTime.now())
      .where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId)))
      .returning()
      .awaitFirstOrNull()
      ?.into(CHATS)
      ?.toChat()
  }

  suspend fun delete(id: KUUID): Int {
    return dsl.deleteFrom(CHATS).where(CHATS.ID.eq(id)).awaitSingle()
  }

  suspend fun delete(id: KUUID, userId: String): Int {
    return dsl.deleteFrom(CHATS).where(CHATS.ID.eq(id).and(CHATS.USER_ID.eq(userId))).awaitSingle()
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
    val total = dsl.selectCount().from(CHATS).awaitSingle().value1() ?: 0

    val chats = mutableListOf<ChatCount>()

    dsl
      .select(CHATS.AGENT_ID, DSL.count(), AGENTS.NAME)
      .from(CHATS)
      .join(AGENTS)
      .on(CHATS.AGENT_ID.eq(AGENTS.ID))
      .groupBy(CHATS.AGENT_ID, AGENTS.NAME)
      .asFlow()
      .map { row -> chats.add(ChatCount(row.value1()!!, row.value3()!!, row.value2())) }
      .toList()

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

    return dsl
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
      .asFlow()
      .map {
        AgentChatsOnDate(
          agentId = it.get("id", KUUID::class.java),
          agentName = it.get("name", String::class.java),
          // If no chats for a given date, this will be null, and we can skip the whole entry
          date = it.get("date", KOffsetDateTime::class.java)?.toLocalDate(),
          amount = it.get("count", Long::class.java),
        )
      }
      .filterNotNull()
      .toList()
  }

  suspend fun getAgentConfigurationMessageCounts(
    agentConfigurationId: KUUID
  ): AgentConfigurationEvaluatedMessageCounts {
    val result =
      dsl
        .select(
          DSL.count(MESSAGE_GROUPS.ID).`as`("total"),
          DSL.sum(DSL.`when`(MESSAGE_GROUP_EVALUATIONS.EVALUATION.isTrue, 1).otherwise(0))
            .`as`("positive"),
          DSL.sum(DSL.`when`(MESSAGE_GROUP_EVALUATIONS.EVALUATION.isFalse, 1).otherwise(0))
            .`as`("negative"),
        )
        .from(CHATS)
        .leftJoin(MESSAGE_GROUPS)
        .on(MESSAGE_GROUPS.PARENT_ID.eq(CHATS.ID))
        .leftJoin(MESSAGE_GROUP_EVALUATIONS)
        .on(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
        .where(CHATS.AGENT_CONFIGURATION_ID.eq(agentConfigurationId))
        .awaitSingle()

    val total = result.get("total", Int::class.java) ?: 0
    val positive = result.get("positive", Int::class.java) ?: 0
    val negative = result.get("negative", Int::class.java) ?: 0

    return AgentConfigurationEvaluatedMessageCounts(total, positive, negative)
  }

  /** List all evaluated messages for a given agent configuration version. */
  suspend fun getAgentConfigurationEvaluatedMessages(
    agentConfigurationId: KUUID,
    evaluation: Boolean? = null,
    pagination: PaginationSort,
  ): CountedList<MessageGroupAggregate> {
    val order = getSortOrderEvaluatedMessages(pagination)
    val (limit, offset) = pagination.limitOffset()

    val where = {
      CHATS.AGENT_CONFIGURATION_ID.eq(agentConfigurationId)
        .and(
          evaluation?.let { MESSAGE_GROUP_EVALUATIONS.EVALUATION.eq(evaluation) }
            ?: DSL.noCondition()
        )
    }

    val count =
      dsl
        .selectCount()
        .from(CHATS)
        .leftJoin(MESSAGE_GROUPS)
        .on(MESSAGE_GROUPS.PARENT_ID.eq(CHATS.ID))
        .leftJoin(MESSAGE_GROUP_EVALUATIONS)
        .on(MESSAGE_GROUPS.ID.eq(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID))
        .where(where())
        .awaitSingle()
        .value1() ?: 0

    val messageGroups = mutableMapOf<KUUID, MessageGroupAggregate>()

    dsl
      .select(
        MESSAGE_GROUPS.ID,
        MESSAGE_GROUPS.PARENT_ID,
        MESSAGE_GROUPS.PARENT_TYPE,
        MESSAGE_GROUPS.CREATED_AT,
        MESSAGES.ID,
        MESSAGES.ORDER,
        MESSAGES.MESSAGE_GROUP_ID,
        MESSAGES.SENDER_TYPE,
        MESSAGES.CONTENT,
        MESSAGES.TOOL_CALLS,
        MESSAGES.TOOL_CALL_ID,
        MESSAGES.FINISH_REASON,
        MESSAGES.CREATED_AT,
        MESSAGE_GROUP_EVALUATIONS.ID,
        MESSAGE_GROUP_EVALUATIONS.EVALUATION,
        MESSAGE_GROUP_EVALUATIONS.FEEDBACK,
        MESSAGE_GROUP_EVALUATIONS.CREATED_AT,
        MESSAGE_GROUP_EVALUATIONS.UPDATED_AT,
      )
      .from(CHATS)
      .leftJoin(MESSAGE_GROUPS)
      .on(MESSAGE_GROUPS.PARENT_ID.eq(CHATS.ID))
      .leftJoin(MESSAGES)
      .on(MESSAGES.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
      .leftJoin(MESSAGE_GROUP_EVALUATIONS)
      .on(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID.eq(MESSAGE_GROUPS.ID))
      .where(where())
      .orderBy(order)
      .limit(limit)
      .offset(offset)
      .asFlow()
      .collect { record ->
        val message = record.into(MESSAGES).toMessage()
        val group = record.into(MESSAGE_GROUPS).toMessageGroup()
        val evaluation =
          if (record.get(MESSAGE_GROUP_EVALUATIONS.ID) != null)
            record.into(MESSAGE_GROUP_EVALUATIONS).toMessageGroupEvaluation()
          else null

        messageGroups
          .computeIfAbsent(group.id) { _ ->
            MessageGroupAggregate(group, mutableListOf(), evaluation)
          }
          .messages
          .add(message)
      }

    return CountedList(count, messageGroups.values.toList())
  }

  private fun getSortOrderEvaluatedMessages(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "createdAt" -> MESSAGE_GROUPS.CREATED_AT
        "evaluation" -> MESSAGE_GROUP_EVALUATIONS.EVALUATION
        else -> MESSAGES.CREATED_AT
      }

    val order = if (sortOrder == SortOrder.DESC) sortField.desc() else sortField.asc()

    return order
  }
}

private fun SearchFiltersAdminChats.toConditions(): Condition {
  val userIdCondition = userId?.let { CHATS.USER_ID.eq(it) } ?: DSL.noCondition()
  val agentIdCondition = agentId?.let { CHATS.AGENT_ID.eq(it) } ?: DSL.noCondition()
  val titleCondition = title?.let { CHATS.TITLE.containsIgnoreCase(it) } ?: DSL.noCondition()

  return DSL.and(userIdCondition, agentIdCondition, titleCondition)
}

fun EvaluateMessage.applyUpdates(
  value: InsertOnDuplicateSetMoreStep<MessageGroupEvaluationsRecord>
): InsertOnDuplicateSetMoreStep<MessageGroupEvaluationsRecord> {
  var value = value
  value = value.set(evaluation, MESSAGE_GROUP_EVALUATIONS.EVALUATION)
  value = value.set(feedback, MESSAGE_GROUP_EVALUATIONS.FEEDBACK)
  return value
}
