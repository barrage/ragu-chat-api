package net.barrage.llmao.app.adapters.whatsapp.repositories

import io.ktor.util.logging.*
import net.barrage.llmao.app.adapters.whatsapp.dto.WhatsAppAgentDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.WhatsAppChatDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.WhatsAppChatWithUserNameDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.toUserDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.toWhatsAppAgentCollectionDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.toWhatsAppAgentDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.toWhatsAppChatDTO
import net.barrage.llmao.app.adapters.whatsapp.dto.toWhatsAppMessageDTO
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgentFull
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppNumber
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.UpdateCollections
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.records.WhatsAppAgentsRecord
import net.barrage.llmao.tables.records.WhatsAppChatsRecord
import net.barrage.llmao.tables.records.WhatsAppMessagesRecord
import net.barrage.llmao.tables.references.USERS
import net.barrage.llmao.tables.references.WHATS_APP_AGENTS
import net.barrage.llmao.tables.references.WHATS_APP_AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.WHATS_APP_CHATS
import net.barrage.llmao.tables.references.WHATS_APP_MESSAGES
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL

internal val LOG =
  KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository")

class WhatsAppRepository(private val dslContext: DSLContext) {
  fun getNumberById(id: KUUID): WhatsAppNumber {
    return dslContext
      .selectFrom(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.ID.eq(id))
      .fetchOne()
      ?.toWhatsAppNumber()
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
  }

  fun addNumber(userId: KUUID, number: PhoneNumber): WhatsAppNumber {
    try {
      return dslContext
        .insertInto(WHATS_APP_NUMBERS)
        .set(WHATS_APP_NUMBERS.USER_ID, userId)
        .set(WHATS_APP_NUMBERS.PHONE_NUMBER, number.phoneNumber)
        .returning()
        .fetchOne()
        ?.toWhatsAppNumber()
        ?: throw AppError.api(ErrorReason.Internal, "Failed to insert WhatsApp number")
    } catch (e: DataAccessException) {
      if (e.message?.contains("whats_app_numbers_phone_number_key") == true) {
        throw AppError.api(
          ErrorReason.EntityAlreadyExists,
          "Phone number '${number.phoneNumber}' is already in use",
        )
      }
      throw AppError.api(ErrorReason.Internal, "Failed to insert WhatsApp number")
    }
  }

  fun getNumbersByUserId(userId: KUUID): List<WhatsAppNumber> {
    return dslContext
      .selectFrom(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.USER_ID.eq(userId))
      .fetch()
      .map { it.toWhatsAppNumber() }
  }

  fun getNumber(number: String): WhatsAppNumber? {
    return dslContext
      .selectFrom(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.PHONE_NUMBER.eq(number))
      .fetchOne()
      ?.toWhatsAppNumber()
  }

  fun updateNumber(numberId: KUUID, updateNumber: PhoneNumber): WhatsAppNumber {
    try {
      return dslContext
        .update(WHATS_APP_NUMBERS)
        .set(WHATS_APP_NUMBERS.PHONE_NUMBER, updateNumber.phoneNumber)
        .where(WHATS_APP_NUMBERS.ID.eq(numberId))
        .returning()
        .fetchOne()
        ?.toWhatsAppNumber()
        ?: throw AppError.api(ErrorReason.Internal, "Failed to update WhatsApp number")
    } catch (e: DataAccessException) {
      if (e.message?.contains("whats_app_numbers_phone_number_key") == true) {
        throw AppError.api(
          ErrorReason.EntityAlreadyExists,
          "Phone number '${updateNumber.phoneNumber}' is already in use",
        )
      }
      throw AppError.api(ErrorReason.Internal, "Failed to insert WhatsApp number")
    }
  }

  fun deleteNumber(numberId: KUUID): Boolean {
    return dslContext
      .deleteFrom(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.ID.eq(numberId))
      .execute() == 1
  }

  fun getAgents(pagination: PaginationSort): CountedList<WhatsAppAgentDTO> {
    val order = getSortOrderAgent(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total = dslContext.selectCount().from(WHATS_APP_AGENTS).fetchOne(0, Int::class.java) ?: 0

    val agents =
      dslContext
        .selectFrom(WHATS_APP_AGENTS)
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch()
        .map { it.toWhatsAppAgentDTO() }

    return CountedList(total, agents)
  }

  fun getAgent(id: KUUID): WhatsAppAgentFull {
    val result =
      dslContext
        .select(
          WHATS_APP_AGENTS.ID,
          WHATS_APP_AGENTS.NAME,
          WHATS_APP_AGENTS.DESCRIPTION,
          WHATS_APP_AGENTS.CONTEXT,
          WHATS_APP_AGENTS.LLM_PROVIDER,
          WHATS_APP_AGENTS.MODEL,
          WHATS_APP_AGENTS.TEMPERATURE,
          WHATS_APP_AGENTS.VECTOR_PROVIDER,
          WHATS_APP_AGENTS.LANGUAGE,
          WHATS_APP_AGENTS.ACTIVE,
          WHATS_APP_AGENTS.EMBEDDING_PROVIDER,
          WHATS_APP_AGENTS.EMBEDDING_MODEL,
          WHATS_APP_AGENTS.LANGUAGE_INSTRUCTION,
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          WHATS_APP_AGENT_COLLECTIONS.ID,
          WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
          WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
          WHATS_APP_AGENT_COLLECTIONS.AMOUNT,
          WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION,
        )
        .from(WHATS_APP_AGENTS)
        .leftJoin(WHATS_APP_AGENT_COLLECTIONS)
        .on(WHATS_APP_AGENTS.ID.eq(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID))
        .where(WHATS_APP_AGENTS.ID.eq(id))
        .fetch()

    if (result.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
    }

    val agent = result.first().into(WHATS_APP_AGENTS).toWhatsAppAgentDTO()
    val collections =
      result
        .filter { it.get(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID) != null }
        .map { it.into(WHATS_APP_AGENT_COLLECTIONS).toWhatsAppAgentCollectionDTO() }

    return WhatsAppAgentFull(agent, collections)
  }

  fun getActiveAgentFull(): WhatsAppAgentFull {
    val result =
      dslContext
        .select(
          WHATS_APP_AGENTS.ID,
          WHATS_APP_AGENTS.NAME,
          WHATS_APP_AGENTS.DESCRIPTION,
          WHATS_APP_AGENTS.CONTEXT,
          WHATS_APP_AGENTS.LLM_PROVIDER,
          WHATS_APP_AGENTS.MODEL,
          WHATS_APP_AGENTS.TEMPERATURE,
          WHATS_APP_AGENTS.VECTOR_PROVIDER,
          WHATS_APP_AGENTS.LANGUAGE,
          WHATS_APP_AGENTS.ACTIVE,
          WHATS_APP_AGENTS.EMBEDDING_PROVIDER,
          WHATS_APP_AGENTS.EMBEDDING_MODEL,
          WHATS_APP_AGENTS.LANGUAGE_INSTRUCTION,
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          WHATS_APP_AGENT_COLLECTIONS.ID,
          WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
          WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
          WHATS_APP_AGENT_COLLECTIONS.AMOUNT,
          WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION,
        )
        .from(WHATS_APP_AGENTS)
        .leftJoin(WHATS_APP_AGENT_COLLECTIONS)
        .on(WHATS_APP_AGENTS.ID.eq(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID))
        .where(WHATS_APP_AGENTS.ACTIVE.isTrue)
        .fetch()

    if (result.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
    }

    val agent = result.first().into(WHATS_APP_AGENTS).toWhatsAppAgentDTO()
    val collections =
      result
        .filter { it.get(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID) != null }
        .map { it.into(WHATS_APP_AGENT_COLLECTIONS).toWhatsAppAgentCollectionDTO() }

    return WhatsAppAgentFull(agent, collections)
  }

  fun createAgent(create: CreateAgent): WhatsAppAgentDTO? {
    return dslContext.transactionResult { tx ->
      // Deactivate all currently active agents
      if (create.active) {
        tx
          .dsl()
          .update(WHATS_APP_AGENTS)
          .set(WHATS_APP_AGENTS.ACTIVE, false)
          .where(WHATS_APP_AGENTS.ACTIVE.eq(true))
          .execute()
      }

      tx
        .dsl()
        .insertInto(WHATS_APP_AGENTS)
        .set(WHATS_APP_AGENTS.NAME, create.name)
        .set(WHATS_APP_AGENTS.DESCRIPTION, create.description)
        .set(WHATS_APP_AGENTS.CONTEXT, create.configuration.context)
        .set(WHATS_APP_AGENTS.LLM_PROVIDER, create.configuration.llmProvider)
        .set(WHATS_APP_AGENTS.MODEL, create.configuration.model)
        .set(WHATS_APP_AGENTS.TEMPERATURE, create.configuration.temperature)
        .set(WHATS_APP_AGENTS.VECTOR_PROVIDER, create.vectorProvider)
        .set(WHATS_APP_AGENTS.LANGUAGE, create.language)
        .set(WHATS_APP_AGENTS.ACTIVE, create.active)
        .set(WHATS_APP_AGENTS.EMBEDDING_PROVIDER, create.embeddingProvider)
        .set(WHATS_APP_AGENTS.EMBEDDING_MODEL, create.embeddingModel)
        .set(
          WHATS_APP_AGENTS.LANGUAGE_INSTRUCTION,
          create.configuration.instructions?.languageInstruction,
        )
        .set(
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          create.configuration.instructions?.summaryInstruction,
        )
        .returning()
        .fetchOne(WhatsAppAgentsRecord::toWhatsAppAgentDTO)
    }
  }

  fun updateCollections(agentId: KUUID, update: UpdateCollections) {
    update.add?.let {
      dslContext
        .batch(
          it.map { (name, amount, instruction) ->
            dslContext
              .insertInto(WHATS_APP_AGENT_COLLECTIONS)
              .set(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID, agentId)
              .set(WHATS_APP_AGENT_COLLECTIONS.COLLECTION, name)
              .set(WHATS_APP_AGENT_COLLECTIONS.AMOUNT, amount)
              .set(WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION, instruction)
              .onConflict(
                WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
                WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
              )
              .doUpdate()
              .set(WHATS_APP_AGENT_COLLECTIONS.AMOUNT, amount)
          }
        )
        .execute()
    }

    update.remove?.let {
      dslContext
        .deleteFrom(WHATS_APP_AGENT_COLLECTIONS)
        .where(
          WHATS_APP_AGENT_COLLECTIONS.AGENT_ID.eq(agentId)
            .and(WHATS_APP_AGENT_COLLECTIONS.COLLECTION.`in`(it))
        )
        .execute()
    }
  }

  fun updateAgent(agentId: KUUID, update: UpdateAgent): WhatsAppAgentDTO {
    val currentAgent =
      dslContext
        .selectFrom(WHATS_APP_AGENTS)
        .where(WHATS_APP_AGENTS.ID.eq(agentId))
        .fetchOne(WhatsAppAgentsRecord::toWhatsAppAgentDTO)

    if (currentAgent == null) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
    }

    try {
      return dslContext.transactionResult { tx ->
        // Check if the update is changing the active state
        if (update.active == true) {
          // Deactivate all currently active agents
          tx
            .dsl()
            .update(WHATS_APP_AGENTS)
            .set(WHATS_APP_AGENTS.ACTIVE, false)
            .where(WHATS_APP_AGENTS.ACTIVE.eq(true))
            .execute()
        } else if (update.active == false) {
          // If deactivating this agent, ensure at least one agent remains active
          val activeAgentsCount =
            tx
              .dsl()
              .selectCount()
              .from(WHATS_APP_AGENTS)
              .where(WHATS_APP_AGENTS.ACTIVE.eq(true))
              .fetchOne(0, Int::class.java) ?: 0

          if (activeAgentsCount <= 1 && currentAgent.active) {
            throw AppError.api(
              ErrorReason.InvalidOperation,
              "Cannot deactivate the last active agent",
            )
          }
        }

        tx
          .dsl()
          .update(WHATS_APP_AGENTS)
          .set(WHATS_APP_AGENTS.NAME, DSL.coalesce(DSL.`val`(update.name), WHATS_APP_AGENTS.NAME))
          .set(
            WHATS_APP_AGENTS.DESCRIPTION,
            DSL.coalesce(DSL.`val`(update.description), WHATS_APP_AGENTS.DESCRIPTION),
          )
          .set(
            WHATS_APP_AGENTS.EMBEDDING_PROVIDER,
            DSL.coalesce(DSL.`val`(update.embeddingProvider), WHATS_APP_AGENTS.EMBEDDING_PROVIDER),
          )
          .set(
            WHATS_APP_AGENTS.EMBEDDING_MODEL,
            DSL.coalesce(DSL.`val`(update.embeddingModel), WHATS_APP_AGENTS.EMBEDDING_MODEL),
          )
          .set(
            WHATS_APP_AGENTS.ACTIVE,
            DSL.coalesce(DSL.`val`(update.active), WHATS_APP_AGENTS.ACTIVE),
          )
          .set(
            WHATS_APP_AGENTS.LANGUAGE,
            DSL.coalesce(DSL.`val`(update.language), WHATS_APP_AGENTS.LANGUAGE),
          )
          .set(
            WHATS_APP_AGENTS.CONTEXT,
            DSL.coalesce(DSL.`val`(update.configuration?.context), WHATS_APP_AGENTS.CONTEXT),
          )
          .set(
            WHATS_APP_AGENTS.LLM_PROVIDER,
            DSL.coalesce(
              DSL.`val`(update.configuration?.llmProvider),
              WHATS_APP_AGENTS.LLM_PROVIDER,
            ),
          )
          .set(
            WHATS_APP_AGENTS.MODEL,
            DSL.coalesce(DSL.`val`(update.configuration?.model), WHATS_APP_AGENTS.MODEL),
          )
          .set(
            WHATS_APP_AGENTS.TEMPERATURE,
            DSL.coalesce(DSL.`val`(update.configuration?.temperature), WHATS_APP_AGENTS.TEMPERATURE),
          )
          .set(
            WHATS_APP_AGENTS.LANGUAGE_INSTRUCTION,
            DSL.coalesce(
              DSL.`val`(update.configuration?.instructions?.languageInstruction),
              WHATS_APP_AGENTS.LANGUAGE_INSTRUCTION,
            ),
          )
          .set(
            WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
            DSL.coalesce(
              DSL.`val`(update.configuration?.instructions?.summaryInstruction),
              WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
            ),
          )
          .where(WHATS_APP_AGENTS.ID.eq(agentId))
          .returning()
          .fetchOne(WhatsAppAgentsRecord::toWhatsAppAgentDTO)
          ?: throw AppError.internal("Failed to update WhatsApp agent")
      }
    } catch (e: Exception) {
      when (e.cause) {
        is AppError -> throw e.cause as AppError
        else -> throw AppError.internal("Failed to update WhatsApp agent: ${e.message}")
      }
    }
  }

  fun deleteAgent(agentId: KUUID) {
    dslContext.deleteFrom(WHATS_APP_AGENTS).where(WHATS_APP_AGENTS.ID.eq(agentId)).execute()
  }

  fun deleteAllCollections(agentId: KUUID) {
    val deleted =
      dslContext
        .delete(WHATS_APP_AGENT_COLLECTIONS)
        .where(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID.eq(agentId))
        .execute()

    LOG.debug("Deleted {} collections from agent {}", deleted, agentId)
  }

  fun getAllChats(pagination: PaginationSort): CountedList<WhatsAppChatWithUserNameDTO> {
    val order = getSortOrderChat(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total = dslContext.selectCount().from(WHATS_APP_CHATS).fetchOne(0, Int::class.java) ?: 0

    val results =
      dslContext
        .select(WHATS_APP_CHATS.ID, WHATS_APP_CHATS.USER_ID, USERS.FULL_NAME)
        .from(WHATS_APP_CHATS)
        .leftJoin(USERS)
        .on(WHATS_APP_CHATS.USER_ID.eq(USERS.ID))
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .fetch()
        .map { it.toWhatsAppChat() }

    return CountedList(total, results)
  }

  fun getChatByUserId(userId: KUUID): WhatsAppChat? {
    return dslContext
      .selectFrom(WHATS_APP_CHATS)
      .where(WHATS_APP_CHATS.USER_ID.eq(userId))
      .fetchOne(WhatsAppChatsRecord::toWhatsAppChat)
  }

  fun getChatWithMessages(id: KUUID): WhatsAppChatWithUserAndMessages {
    val result =
      dslContext
        .select(
          WHATS_APP_CHATS.ID,
          WHATS_APP_CHATS.USER_ID,

          // WHATS_APP_MESSAGES columns
          WHATS_APP_MESSAGES.ID,
          WHATS_APP_MESSAGES.SENDER,
          WHATS_APP_MESSAGES.SENDER_TYPE,
          WHATS_APP_MESSAGES.CONTENT,
          WHATS_APP_MESSAGES.CHAT_ID,
          WHATS_APP_MESSAGES.RESPONSE_TO,

          // USERS columns
          USERS.ID,
          USERS.EMAIL,
          USERS.FULL_NAME,
          USERS.FIRST_NAME,
          USERS.LAST_NAME,
          USERS.ACTIVE,
          USERS.ROLE,
        )
        .from(WHATS_APP_CHATS)
        .leftJoin(WHATS_APP_MESSAGES)
        .on(WHATS_APP_CHATS.ID.eq(WHATS_APP_MESSAGES.CHAT_ID))
        .leftJoin(USERS)
        .on(WHATS_APP_CHATS.USER_ID.eq(USERS.ID))
        .where(WHATS_APP_CHATS.ID.eq(id))
        .orderBy(WHATS_APP_MESSAGES.CREATED_AT.desc())
        .fetch()

    if (result.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp chat not found")
    }

    val chat = result.first().into(WHATS_APP_CHATS).toWhatsAppChatDTO()
    val user = result.first().into(USERS).toUserDTO()
    val messages =
      result
        .filter { it.get(WHATS_APP_MESSAGES.CHAT_ID) != null }
        .map { it.into(WHATS_APP_MESSAGES).toWhatsAppMessageDTO() }

    return WhatsAppChatWithUserAndMessages(chat, user, messages)
  }

  fun storeChat(id: KUUID, userId: KUUID): WhatsAppChat {
    return dslContext
      .insertInto(WHATS_APP_CHATS)
      .set(WHATS_APP_CHATS.ID, id)
      .set(WHATS_APP_CHATS.USER_ID, userId)
      .returning()
      .fetchOne()
      ?.toWhatsAppChat()
      ?: throw AppError.api(ErrorReason.Internal, "Failed to insert WhatsApp number")
  }

  fun getMessages(chatId: KUUID, limit: Int? = null): List<WhatsAppMessage> {
    return dslContext
      .selectFrom(WHATS_APP_MESSAGES)
      .where(WHATS_APP_MESSAGES.CHAT_ID.eq(chatId))
      .orderBy(WHATS_APP_MESSAGES.CREATED_AT.desc())
      .apply { limit?.let { limit(it) } }
      .fetchInto(WhatsAppMessagesRecord::class.java)
      .map { it.toWhatsAppMessage() }
  }

  fun insertUserMessage(chatId: KUUID, userId: KUUID, proompt: String): WhatsAppMessage {
    return dslContext
      .insertInto(WHATS_APP_MESSAGES)
      .set(WHATS_APP_MESSAGES.CHAT_ID, chatId)
      .set(WHATS_APP_MESSAGES.SENDER, userId)
      .set(WHATS_APP_MESSAGES.SENDER_TYPE, "user")
      .set(WHATS_APP_MESSAGES.CONTENT, proompt)
      .returning()
      .fetchOne(WhatsAppMessagesRecord::toWhatsAppMessage)!!
  }

  fun insertAssistantMessage(chatId: KUUID, agentId: KUUID, messageId: KUUID, response: String) {
    dslContext
      .insertInto(WHATS_APP_MESSAGES)
      .set(WHATS_APP_MESSAGES.CHAT_ID, chatId)
      .set(WHATS_APP_MESSAGES.SENDER, agentId)
      .set(WHATS_APP_MESSAGES.SENDER_TYPE, "assistant")
      .set(WHATS_APP_MESSAGES.CONTENT, response)
      .set(WHATS_APP_MESSAGES.RESPONSE_TO, messageId)
      .execute()
  }

  private fun getSortOrderAgent(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "name" -> WHATS_APP_AGENTS.NAME
        "description" -> WHATS_APP_AGENTS.DESCRIPTION
        "context" -> WHATS_APP_AGENTS.CONTEXT
        "llmProvider" -> WHATS_APP_AGENTS.LLM_PROVIDER
        "createdAt" -> WHATS_APP_AGENTS.CREATED_AT
        "updatedAt" -> WHATS_APP_AGENTS.UPDATED_AT
        "active" -> WHATS_APP_AGENTS.ACTIVE
        else -> WHATS_APP_AGENTS.NAME
      }

    val order =
      if (sortOrder == SortOrder.DESC) {
        sortField.desc()
      } else {
        sortField.asc()
      }

    return order
  }

  private fun getSortOrderChat(pagination: PaginationSort): SortField<out Any> {
    val (sortBy, sortOrder) = pagination.sorting()
    val sortField =
      when (sortBy) {
        "name" -> USERS.FULL_NAME
        "createdAt" -> WHATS_APP_CHATS.CREATED_AT
        "updatedAt" -> WHATS_APP_CHATS.UPDATED_AT
        else -> WHATS_APP_CHATS.CREATED_AT
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

private fun Record.toWhatsAppChat(): WhatsAppChatWithUserNameDTO {
  return WhatsAppChatWithUserNameDTO(
    WhatsAppChatDTO(
      id = this[WHATS_APP_CHATS.ID] as KUUID,
      userId = this[WHATS_APP_CHATS.USER_ID] as KUUID,
    ),
    fullName = this[USERS.FULL_NAME]!!,
  )
}
