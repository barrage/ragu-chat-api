package net.barrage.llmao.repositories

import kotlinx.serialization.Serializable
import net.barrage.llmao.models.SessionData
import net.barrage.llmao.models.toSessionData
import net.barrage.llmao.plugins.Database.dslContext
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.SessionsRecord
import net.barrage.llmao.tables.references.SESSIONS
import java.time.OffsetDateTime

@Serializable
class SessionRepository {
    fun create(sessionId: KUUID, userId: KUUID) {
        dslContext.insertInto(SESSIONS)
            .set(SESSIONS.ID, sessionId)
            .set(SESSIONS.USER_ID, userId)
            .set(SESSIONS.CREATED_AT, OffsetDateTime.now())
            .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now().plusDays(1))
            .execute()
    }

    fun get(id: KUUID): SessionData? {
        return dslContext.selectFrom(SESSIONS)
            .where(SESSIONS.ID.eq(id))
            .fetchOne(SessionsRecord::toSessionData)
    }

    fun extend(id: KUUID) {
        dslContext.update(SESSIONS)
            .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now().plusDays(1))
            .where(SESSIONS.ID.eq(id))
            .execute()
    }

    fun expire(id: KUUID) {
        dslContext.update(SESSIONS)
            .set(SESSIONS.UPDATED_AT, OffsetDateTime.now())
            .set(SESSIONS.EXPIRES_AT, OffsetDateTime.now())
            .where(SESSIONS.ID.eq(id))
            .execute()
    }

    // TODO: implement cronjob to delete expired sessions
    fun delete(): Int {
        return dslContext.deleteFrom(SESSIONS)
            .where(SESSIONS.EXPIRES_AT.lt(OffsetDateTime.now()))
            .execute()
    }
}