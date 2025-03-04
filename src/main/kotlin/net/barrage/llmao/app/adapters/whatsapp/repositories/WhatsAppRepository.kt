package net.barrage.llmao.app.adapters.whatsapp.repositories

import io.ktor.util.logging.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.app.adapters.whatsapp.models.PhoneNumber
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserAndMessages
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppChatWithUserName
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.WhatsAppNumber
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppChat
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppMessage
import net.barrage.llmao.app.adapters.whatsapp.models.toWhatsAppNumber
import net.barrage.llmao.core.models.common.CountedList
import net.barrage.llmao.core.models.common.PaginationSort
import net.barrage.llmao.core.models.common.SortOrder
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.tables.references.USERS
import net.barrage.llmao.tables.references.WHATS_APP_CHATS
import net.barrage.llmao.tables.references.WHATS_APP_MESSAGES
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.SortField
import org.jooq.exception.DataAccessException

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
          USERS.AVATAR,
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
          USERS.AVATAR,
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

  suspend fun getMessages(chatId: KUUID): List<WhatsAppMessage> {
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
      .orderBy(WHATS_APP_MESSAGES.CREATED_AT.asc(), WHATS_APP_MESSAGES.SENDER_TYPE.asc())
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
    avatar = this[USERS.AVATAR],
  )
}
