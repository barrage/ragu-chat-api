package net.barrage.llmao.app.workflow.chat.whatsapp

import io.ktor.util.logging.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.app.workflow.chat.whatsapp.model.UpdateNumber
import net.barrage.llmao.app.workflow.chat.whatsapp.model.WhatsAppNumber
import net.barrage.llmao.app.workflow.chat.whatsapp.model.toWhatsAppNumber
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
import net.barrage.llmao.core.types.KUUID
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException

internal val LOG = KtorSimpleLogger("net.barrage.llmao.app.adapters.whatsapp.WhatsAppRepository")

class WhatsAppRepository(private val dslContext: DSLContext) {
  suspend fun getNumberById(id: KUUID): WhatsAppNumber {
    return dslContext
      .select(
        WHATS_APP_NUMBERS.ID,
        WHATS_APP_NUMBERS.USER_ID,
        WHATS_APP_NUMBERS.USERNAME,
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

  suspend fun addNumber(userId: String, username: String, number: String): WhatsAppNumber {
    try {
      return dslContext
        .insertInto(WHATS_APP_NUMBERS)
        .set(WHATS_APP_NUMBERS.USER_ID, userId)
        .set(WHATS_APP_NUMBERS.USERNAME, username)
        .set(WHATS_APP_NUMBERS.PHONE_NUMBER, number)
        .returning()
        .awaitSingle()
        .toWhatsAppNumber()
    } catch (e: DataAccessException) {
      if (e.message?.contains("whats_app_numbers_phone_number_key") == true) {
        throw AppError.api(
          ErrorReason.EntityAlreadyExists,
          "Phone number '${number}' is already in use",
          original = e,
        )
      }
      throw AppError.api(ErrorReason.Internal, "Failed to insert WhatsApp number", original = e)
    }
  }

  suspend fun getNumbersByUserId(userId: String): List<WhatsAppNumber> {
    return dslContext
      .select(
        WHATS_APP_NUMBERS.ID,
        WHATS_APP_NUMBERS.USER_ID,
        WHATS_APP_NUMBERS.USERNAME,
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
        WHATS_APP_NUMBERS.USERNAME,
        WHATS_APP_NUMBERS.CREATED_AT,
        WHATS_APP_NUMBERS.UPDATED_AT,
      )
      .from(WHATS_APP_NUMBERS)
      .where(WHATS_APP_NUMBERS.PHONE_NUMBER.eq(number))
      .awaitFirstOrNull()
      ?.into(WHATS_APP_NUMBERS)
      ?.toWhatsAppNumber()
  }

  suspend fun updateNumber(numberId: KUUID, updateNumber: UpdateNumber): WhatsAppNumber {
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
}
