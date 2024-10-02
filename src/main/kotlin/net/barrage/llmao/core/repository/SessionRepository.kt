package net.barrage.llmao.core.repository

import java.time.OffsetDateTime
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.toSessionData
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.error.internalError
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.references.SESSIONS

@Serializable
class SessionRepository {
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
      throw internalError()
    }

    return session.toSessionData()
  }

  fun get(id: KUUID): Session? {
    return dslContext
      .selectFrom(SESSIONS)
      .where(SESSIONS.ID.eq(id))
      .fetchOne(SessionsRecord::toSessionData)
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
