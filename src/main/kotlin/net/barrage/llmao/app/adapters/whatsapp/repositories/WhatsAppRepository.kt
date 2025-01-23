package net.barrage.llmao.app.adapters.whatsapp.repositories

import io.ktor.util.logging.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgent
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppAgentFull
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.models.toAgentCollection
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppAgent
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppAgentCurrent
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppNumber
import net.barrage.llmao.core.models.CollectionInsert
import net.barrage.llmao.core.models.CollectionRemove
import net.barrage.llmao.core.models.CreateAgent
import net.barrage.llmao.core.models.UpdateAgent
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
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
import org.jooq.impl.DSL.excluded
import org.jooq.kotlin.coroutines.transactionCoroutine

internal val LOG =
  KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp.repositories.WhatsAppRepository")

class WhatsAppRepository(private val dslContext: DSLContext) {
  suspend fun getNumberById(id: KUUID): WhatsAppNumber {
    return dslContext
      .select(
        WHATS_APP_NUMBERS.ID,
        WHATS_APP_NUMBERS.USER_ID,
        WHATS_APP_NUMBERS.PHONE_NUMBER,
        WHATS_APP_NUMBERS.CREATED_AT,
        WHATS_APP_NUMBERS.UPDATED_AT,
      )
      .from(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.ID.eq(id))
      .awaitSingle()
      ?.into(WHATS_APP_NUMBERS)
      ?.toWhatsAppNumber()
      ?: throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp number not found")
  }

  suspend fun addNumber(userId: KUUID, number: PhoneNumber): WhatsAppNumber {
    try {
      return dslContext
        .insertInto(WHATS_APP_NUMBERS)
        .set(WHATS_APP_NUMBERS.USER_ID, userId)
        .set(WHATS_APP_NUMBERS.PHONE_NUMBER, number.phoneNumber)
        .returning()
        .awaitSingle()
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

  suspend fun getNumbersByUserId(userId: KUUID): List<WhatsAppNumber> {
    return dslContext
      .select(
        WHATS_APP_NUMBERS.ID,
        WHATS_APP_NUMBERS.USER_ID,
        WHATS_APP_NUMBERS.PHONE_NUMBER,
        WHATS_APP_NUMBERS.CREATED_AT,
        WHATS_APP_NUMBERS.UPDATED_AT,
      )
      .from(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.USER_ID.eq(userId))
      .asFlow()
      .map { it.into(WHATS_APP_NUMBERS).toWhatsAppNumber() }
      .toList()
  }

  suspend fun getNumber(number: String): WhatsAppNumber? {
    return dslContext
      .select(
        WHATS_APP_NUMBERS.ID,
        WHATS_APP_NUMBERS.USER_ID,
        WHATS_APP_NUMBERS.PHONE_NUMBER,
        WHATS_APP_NUMBERS.CREATED_AT,
        WHATS_APP_NUMBERS.UPDATED_AT,
      )
      .from(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.PHONE_NUMBER.eq(number))
      .awaitFirstOrNull()
      ?.into(WHATS_APP_NUMBERS)
      ?.toWhatsAppNumber()
  }

  suspend fun updateNumber(numberId: KUUID, updateNumber: PhoneNumber): WhatsAppNumber {
    try {
      return dslContext
        .update(WHATS_APP_NUMBERS)
        .set(WHATS_APP_NUMBERS.PHONE_NUMBER, updateNumber.phoneNumber)
        .where(WHATS_APP_NUMBERS.ID.eq(numberId))
        .returning()
        .awaitSingle()
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

  suspend fun deleteNumber(numberId: KUUID): Boolean {
    return dslContext
      .deleteFrom(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.ID.eq(numberId))
      .awaitSingle() == 1
  }

  suspend fun getAgents(pagination: PaginationSort): CountedList<WhatsAppAgent> {
    val order = getSortOrderAgent(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total = dslContext.selectCount().from(WHATS_APP_AGENTS).awaitSingle().value1() ?: 0

    val agents =
      dslContext
        .select(
          WHATS_APP_AGENTS.ID,
          WHATS_APP_AGENTS.NAME,
          WHATS_APP_AGENTS.DESCRIPTION,
          WHATS_APP_AGENTS.CONTEXT,
          WHATS_APP_AGENTS.LLM_PROVIDER,
          WHATS_APP_AGENTS.MODEL,
          WHATS_APP_AGENTS.TEMPERATURE,
          WHATS_APP_AGENTS.LANGUAGE,
          WHATS_APP_AGENTS.ACTIVE,
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          WHATS_APP_AGENTS.CREATED_AT,
          WHATS_APP_AGENTS.UPDATED_AT,
        )
        .from(WHATS_APP_AGENTS)
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { it.into(WHATS_APP_AGENTS).toWhatsAppAgent() }
        .toList()

    return CountedList(total, agents)
  }

  suspend fun getAgent(id: KUUID): WhatsAppAgentFull {
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
          WHATS_APP_AGENTS.LANGUAGE,
          WHATS_APP_AGENTS.ACTIVE,
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          WHATS_APP_AGENTS.CREATED_AT,
          WHATS_APP_AGENTS.UPDATED_AT,
          WHATS_APP_AGENT_COLLECTIONS.ID,
          WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
          WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
          WHATS_APP_AGENT_COLLECTIONS.AMOUNT,
          WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION,
          WHATS_APP_AGENT_COLLECTIONS.CREATED_AT,
          WHATS_APP_AGENT_COLLECTIONS.UPDATED_AT,
          WHATS_APP_AGENT_COLLECTIONS.EMBEDDING_PROVIDER,
          WHATS_APP_AGENT_COLLECTIONS.EMBEDDING_MODEL,
          WHATS_APP_AGENT_COLLECTIONS.VECTOR_PROVIDER,
        )
        .from(WHATS_APP_AGENTS)
        .leftJoin(WHATS_APP_AGENT_COLLECTIONS)
        .on(WHATS_APP_AGENTS.ID.eq(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID))
        .where(WHATS_APP_AGENTS.ID.eq(id))
        .asFlow()
        .toList()

    if (result.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
    }

    val agent = result.first().into(WHATS_APP_AGENTS).toWhatsAppAgent()
    val collections =
      result
        .filter { it.get(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID) != null }
        .map { it.into(WHATS_APP_AGENT_COLLECTIONS).toAgentCollection() }

    return WhatsAppAgentFull(agent, collections)
  }

  suspend fun getActiveAgentFull(): WhatsAppAgentFull {
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
          WHATS_APP_AGENTS.LANGUAGE,
          WHATS_APP_AGENTS.ACTIVE,
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          WHATS_APP_AGENTS.CREATED_AT,
          WHATS_APP_AGENTS.UPDATED_AT,
          WHATS_APP_AGENT_COLLECTIONS.ID,
          WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
          WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION,
          WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
          WHATS_APP_AGENT_COLLECTIONS.EMBEDDING_MODEL,
          WHATS_APP_AGENT_COLLECTIONS.VECTOR_PROVIDER,
          WHATS_APP_AGENT_COLLECTIONS.EMBEDDING_PROVIDER,
          WHATS_APP_AGENT_COLLECTIONS.AMOUNT,
          WHATS_APP_AGENT_COLLECTIONS.CREATED_AT,
          WHATS_APP_AGENT_COLLECTIONS.UPDATED_AT,
        )
        .from(WHATS_APP_AGENTS)
        .leftJoin(WHATS_APP_AGENT_COLLECTIONS)
        .on(WHATS_APP_AGENTS.ID.eq(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID))
        .where(WHATS_APP_AGENTS.ACTIVE.isTrue)
        .asFlow()
        .toList()

    if (result.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
    }

    val agent = result.first().into(WHATS_APP_AGENTS).toWhatsAppAgent()
    val collections =
      result
        .filter { it.get(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID) != null }
        .map { it.into(WHATS_APP_AGENT_COLLECTIONS).toAgentCollection() }

    return WhatsAppAgentFull(agent, collections)
  }

  suspend fun createAgent(create: CreateAgent): WhatsAppAgent? {
    return dslContext.transactionCoroutine { tx ->
      // Deactivate all currently active agents
      if (create.active) {
        tx
          .dsl()
          .update(WHATS_APP_AGENTS)
          .set(WHATS_APP_AGENTS.ACTIVE, false)
          .where(WHATS_APP_AGENTS.ACTIVE.eq(true))
          .awaitSingle()
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
        .set(WHATS_APP_AGENTS.LANGUAGE, create.language)
        .set(WHATS_APP_AGENTS.ACTIVE, create.active)
        .set(
          WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
          create.configuration.instructions?.summaryInstruction,
        )
        .returning()
        .awaitSingle()
        ?.toWhatsAppAgent()
    }
  }

  suspend fun updateCollections(
    agentId: KUUID,
    add: List<CollectionInsert>?,
    remove: List<CollectionRemove>?,
  ) {
    dslContext.transactionCoroutine { tx ->
      add?.let { additions ->
        if (additions.isNotEmpty()) {
          try {
            tx
              .dsl()
              .insertInto(
                WHATS_APP_AGENT_COLLECTIONS,
                WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
                WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
                WHATS_APP_AGENT_COLLECTIONS.EMBEDDING_PROVIDER,
                WHATS_APP_AGENT_COLLECTIONS.EMBEDDING_MODEL,
                WHATS_APP_AGENT_COLLECTIONS.VECTOR_PROVIDER,
                WHATS_APP_AGENT_COLLECTIONS.AMOUNT,
                WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION,
              )
              .apply {
                additions.forEach { collection ->
                  values(
                    agentId,
                    collection.info.name,
                    collection.info.embeddingProvider,
                    collection.info.embeddingModel,
                    collection.info.vectorProvider,
                    collection.amount,
                    collection.instruction,
                  )
                }
              }
              .onConflict(
                WHATS_APP_AGENT_COLLECTIONS.AGENT_ID,
                WHATS_APP_AGENT_COLLECTIONS.COLLECTION,
                WHATS_APP_AGENT_COLLECTIONS.VECTOR_PROVIDER,
              )
              .doUpdate()
              .set(WHATS_APP_AGENT_COLLECTIONS.AMOUNT, excluded(WHATS_APP_AGENT_COLLECTIONS.AMOUNT))
              .set(
                WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION,
                excluded(WHATS_APP_AGENT_COLLECTIONS.INSTRUCTION),
              )
              .awaitSingle()
          } catch (e: DataAccessException) {
            LOG.error("Error adding collections", e)
            throw AppError.internal(e.message ?: "Failed to add collections")
          }
        }
      }

      remove?.let { removals ->
        if (removals.isNotEmpty()) {
          try {
            tx
              .dsl()
              .deleteFrom(WHATS_APP_AGENT_COLLECTIONS)
              .where(
                DSL.or(
                  removals.map { collection ->
                    WHATS_APP_AGENT_COLLECTIONS.AGENT_ID.eq(agentId)
                      .and(WHATS_APP_AGENT_COLLECTIONS.COLLECTION.eq(collection.name))
                      .and(WHATS_APP_AGENT_COLLECTIONS.VECTOR_PROVIDER.eq(collection.provider))
                  }
                )
              )
              .awaitSingle()
          } catch (e: DataAccessException) {
            LOG.error("Error removing collections", e)
            throw AppError.internal(e.message ?: "Failed to remove collections")
          }
        }
      }
    }
  }

  suspend fun removeCollectionFromAllAgents(collectionName: String, provider: String) {
    dslContext
      .deleteFrom(WHATS_APP_AGENT_COLLECTIONS)
      .where(
        WHATS_APP_AGENT_COLLECTIONS.COLLECTION.eq(collectionName)
          .and(WHATS_APP_AGENT_COLLECTIONS.VECTOR_PROVIDER.eq(provider))
      )
      .awaitSingle()
  }

  suspend fun updateAgent(agentId: KUUID, update: UpdateAgent): WhatsAppAgent {
    val currentAgent =
      dslContext
        .select(WHATS_APP_AGENTS.ID, WHATS_APP_AGENTS.ACTIVE)
        .from(WHATS_APP_AGENTS)
        .where(WHATS_APP_AGENTS.ID.eq(agentId))
        .awaitFirstOrNull()
        ?.into(WHATS_APP_AGENTS)
        ?.toWhatsAppAgentCurrent()

    if (currentAgent == null) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp agent not found")
    }

    try {
      return dslContext.transactionCoroutine { tx ->
        // Check if the update is changing the active state
        if (update.active == true) {
          // Deactivate all currently active agents
          tx
            .dsl()
            .update(WHATS_APP_AGENTS)
            .set(WHATS_APP_AGENTS.ACTIVE, false)
            .where(WHATS_APP_AGENTS.ACTIVE.eq(true))
            .awaitSingle()
        } else if (update.active == false) {
          // If deactivating this agent, ensure at least one agent remains active
          val activeAgentsCount =
            tx
              .dsl()
              .selectCount()
              .from(WHATS_APP_AGENTS)
              .where(WHATS_APP_AGENTS.ACTIVE.eq(true))
              .awaitSingle()
              .value1() ?: 0
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
            WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
            DSL.coalesce(
              DSL.`val`(update.configuration?.instructions?.summaryInstruction),
              WHATS_APP_AGENTS.SUMMARY_INSTRUCTION,
            ),
          )
          .where(WHATS_APP_AGENTS.ID.eq(agentId))
          .returning()
          .awaitSingle()
          ?.toWhatsAppAgent() ?: throw AppError.internal("Failed to update WhatsApp agent")
      }
    } catch (e: Exception) {
      when {
        e.cause is AppError -> throw e.cause as AppError
        e.cause?.cause is AppError -> throw e.cause?.cause as AppError
        else -> throw AppError.internal("Failed to update WhatsApp agent: ${e.message}")
      }
    }
  }

  suspend fun deleteAgent(agentId: KUUID) {
    dslContext.deleteFrom(WHATS_APP_AGENTS).where(WHATS_APP_AGENTS.ID.eq(agentId)).awaitSingle()
  }

  suspend fun deleteAllCollections(agentId: KUUID) {
    val deleted =
      dslContext
        .delete(WHATS_APP_AGENT_COLLECTIONS)
        .where(WHATS_APP_AGENT_COLLECTIONS.AGENT_ID.eq(agentId))
        .awaitSingle()

    LOG.debug("Deleted {} collections from agent {}", deleted, agentId)
  }

  suspend fun getAllChats(pagination: PaginationSort): CountedList<WhatsAppChatWithUserName> {
    val order = getSortOrderChat(pagination)
    val (limit, offset) = pagination.limitOffset()

    val total = dslContext.selectCount().from(WHATS_APP_CHATS).awaitSingle().value1() ?: 0

    val results =
      dslContext
        .select(
          WHATS_APP_CHATS.ID,
          WHATS_APP_CHATS.USER_ID,
          WHATS_APP_CHATS.CREATED_AT,
          WHATS_APP_CHATS.UPDATED_AT,
          USERS.FULL_NAME,
        )
        .from(WHATS_APP_CHATS)
        .leftJoin(USERS)
        .on(WHATS_APP_CHATS.USER_ID.eq(USERS.ID))
        .orderBy(order)
        .limit(limit)
        .offset(offset)
        .asFlow()
        .map { it.toWhatsAppChatWithUserName() }
        .toList()

    return CountedList(total, results)
  }

  suspend fun getChatByUserId(userId: KUUID): WhatsAppChat? {
    return dslContext
      .select(
        WHATS_APP_CHATS.ID,
        WHATS_APP_CHATS.USER_ID,
        WHATS_APP_CHATS.CREATED_AT,
        WHATS_APP_CHATS.UPDATED_AT,
      )
      .from(WHATS_APP_CHATS)
      .where(WHATS_APP_CHATS.USER_ID.eq(userId))
      .awaitFirstOrNull()
      ?.into(WHATS_APP_CHATS)
      ?.toWhatsAppChat()
  }

  suspend fun getChatWithMessages(id: KUUID): WhatsAppChatWithUserAndMessages {
    val result =
      dslContext
        .select(
          WHATS_APP_CHATS.ID,
          WHATS_APP_CHATS.USER_ID,
          WHATS_APP_CHATS.CREATED_AT,
          WHATS_APP_CHATS.UPDATED_AT,

          // WHATS_APP_MESSAGES columns
          WHATS_APP_MESSAGES.ID,
          WHATS_APP_MESSAGES.SENDER,
          WHATS_APP_MESSAGES.SENDER_TYPE,
          WHATS_APP_MESSAGES.CONTENT,
          WHATS_APP_MESSAGES.CHAT_ID,
          WHATS_APP_MESSAGES.RESPONSE_TO,
          WHATS_APP_MESSAGES.CREATED_AT,
          WHATS_APP_MESSAGES.UPDATED_AT,

          // USERS columns
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
        .from(WHATS_APP_CHATS)
        .leftJoin(WHATS_APP_MESSAGES)
        .on(WHATS_APP_CHATS.ID.eq(WHATS_APP_MESSAGES.CHAT_ID))
        .leftJoin(USERS)
        .on(WHATS_APP_CHATS.USER_ID.eq(USERS.ID))
        .where(WHATS_APP_CHATS.ID.eq(id))
        .orderBy(WHATS_APP_MESSAGES.CREATED_AT.desc())
        .asFlow()
        .toList()

    if (result.isEmpty()) {
      throw AppError.api(ErrorReason.EntityDoesNotExist, "WhatsApp chat not found")
    }

    val chat = result.first().into(WHATS_APP_CHATS).toWhatsAppChat()
    val user = result.first().into(USERS).toUser()
    val messages =
      result
        .filter { it.get(WHATS_APP_MESSAGES.CHAT_ID) != null }
        .map { it.into(WHATS_APP_MESSAGES).toWhatsAppMessage() }

    return WhatsAppChatWithUserAndMessages(chat, user, messages)
  }

  suspend fun storeChat(id: KUUID, userId: KUUID): WhatsAppChat {
    return dslContext
      .insertInto(WHATS_APP_CHATS)
      .set(WHATS_APP_CHATS.ID, id)
      .set(WHATS_APP_CHATS.USER_ID, userId)
      .returning()
      .awaitSingle()
      ?.toWhatsAppChat()
      ?: throw AppError.api(ErrorReason.Internal, "Failed to insert WhatsApp number")
  }

  suspend fun getMessages(chatId: KUUID, limit: Int? = null): List<WhatsAppMessage> {
    return dslContext
      .select(
        WHATS_APP_MESSAGES.ID,
        WHATS_APP_MESSAGES.SENDER,
        WHATS_APP_MESSAGES.SENDER_TYPE,
        WHATS_APP_MESSAGES.CONTENT,
        WHATS_APP_MESSAGES.CHAT_ID,
        WHATS_APP_MESSAGES.RESPONSE_TO,
        WHATS_APP_MESSAGES.CREATED_AT,
        WHATS_APP_MESSAGES.UPDATED_AT,
      )
      .from(WHATS_APP_MESSAGES)
      .where(WHATS_APP_MESSAGES.CHAT_ID.eq(chatId))
      .orderBy(WHATS_APP_MESSAGES.CREATED_AT.desc())
      .apply { limit?.let { limit(it) } }
      .asFlow()
      .map { it.into(WHATS_APP_MESSAGES).toWhatsAppMessage() }
      .toList()
  }

  suspend fun insertUserMessage(chatId: KUUID, userId: KUUID, proompt: String): WhatsAppMessage {
    return dslContext
      .insertInto(WHATS_APP_MESSAGES)
      .set(WHATS_APP_MESSAGES.CHAT_ID, chatId)
      .set(WHATS_APP_MESSAGES.SENDER, userId)
      .set(WHATS_APP_MESSAGES.SENDER_TYPE, "user")
      .set(WHATS_APP_MESSAGES.CONTENT, proompt)
      .returning()
      .awaitSingle()
      ?.toWhatsAppMessage()!!
  }

  suspend fun insertAssistantMessage(
    chatId: KUUID,
    agentId: KUUID,
    messageId: KUUID,
    response: String,
  ) {
    dslContext
      .insertInto(WHATS_APP_MESSAGES)
      .set(WHATS_APP_MESSAGES.CHAT_ID, chatId)
      .set(WHATS_APP_MESSAGES.SENDER, agentId)
      .set(WHATS_APP_MESSAGES.SENDER_TYPE, "assistant")
      .set(WHATS_APP_MESSAGES.CONTENT, response)
      .set(WHATS_APP_MESSAGES.RESPONSE_TO, messageId)
      .awaitSingle()
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

private fun Record.toWhatsAppChatWithUserName(): WhatsAppChatWithUserName {
  return WhatsAppChatWithUserName(
    WhatsAppChat(
      id = this[WHATS_APP_CHATS.ID] as KUUID,
      userId = this[WHATS_APP_CHATS.USER_ID] as KUUID,
      createdAt = this[WHATS_APP_CHATS.CREATED_AT] as KOffsetDateTime,
      updatedAt = this[WHATS_APP_CHATS.UPDATED_AT] as KOffsetDateTime,
    ),
    fullName = this[USERS.FULL_NAME]!!,
  )
}
