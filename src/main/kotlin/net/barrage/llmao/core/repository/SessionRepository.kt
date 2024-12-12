package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.toSessionData
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.AppError
import net.barrage.llmao.tables.references.SESSIONS
import org.jooq.DSLContext

@Serializable
class SessionRepository(private val dslContext: DSLContext) {
  fun create(sessionId: KUUID, userId: KUUID): Session {
    val session =
      dslContext
        .insertInto(SESSIONS)
        .set(SESSIONS.ID, sessionId)
        .set(SESSIONS.USER_ID, userId)
        .set(SESSIONS.CREATED_AT, OffsetDateTime.now())
        .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
        .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now().plusDays(1))
        .returning(
          SESSIONS.ID,
          SESSIONS.USER_ID,
          SESSIONS.CREATED_AT,
          SESSIONS.UPDATED_AT,
          SESSIONS.EXPIRES_AT,
        )
        .fetchOne()

    // This can only happen on constraint failures. Since we
    // are generating a unique UUID every time, this should never
    // happen.
    if (session == null) {
      throw AppError.internal()
    }

    return session.toSessionData()
  }

  fun get(id: KUUID): Session? {
    return dslContext
      .select(
        SESSIONS.ID,
        SESSIONS.USER_ID,
        SESSIONS.CREATED_AT,
        SESSIONS.UPDATED_AT,
        SESSIONS.EXPIRES_AT,
      )
      .from(SESSIONS)
      .where(SESSIONS.ID.eq(id))
      .fetchOne()
      ?.into(SESSIONS)
      ?.toSessionData()
  }

  fun getActiveByUserId(id: KUUID): List<Session?> {
    return dslContext
      .select(
        SESSIONS.ID,
        SESSIONS.USER_ID,
        SESSIONS.CREATED_AT,
        SESSIONS.UPDATED_AT,
        SESSIONS.EXPIRES_AT,
      )
      .from(SESSIONS)
      .where(SESSIONS.USER_ID.eq(id).and(SESSIONS.EXPIRES_AT.gt(OffsetDateTime.now())))
      .fetch()
      .map { it.into(SESSIONS).toSessionData() }
  }

  fun extend(id: KUUID) {
    dslContext
      .update(SESSIONS)
      .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
      .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now().plusDays(1))
      .where(SESSIONS.ID.eq(id))
      .execute()
  }

  fun expire(id: KUUID) {
    dslContext
      .update(SESSIONS)
      .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
      .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now())
      .where(SESSIONS.ID.eq(id))
      .execute()
  }

  // TODO: implement cronjob to delete expired sessions
  fun delete(): Int {
    return dslContext
      .deleteFrom(SESSIONS)
      .where(SESSIONS.EXPIRES_AT.lt(OffsetDateTime.now()))
      .execute()
  }
}
