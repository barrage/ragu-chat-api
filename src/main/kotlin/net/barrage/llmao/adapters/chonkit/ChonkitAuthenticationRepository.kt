package net.barrage.llmao.adapters.chonkit

import java.time.OffsetDateTime
import net.barrage.llmao.adapters.chonkit.models.ChonkitSession
import net.barrage.llmao.adapters.chonkit.models.toChonkitSession
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.models.toUser
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.CHONKIT_SESSIONS
import net.barrage.llmao.tables.references.USERS
import org.jooq.DSLContext

class ChonkitAuthenticationRepository(private val dsl: DSLContext) {
  /**
   * Get an active session for the given user and refresh token.
   *
   * @param userId The user ID to search for.
   * @param refreshToken The refresh token to search for.
   * @return The active session, or `null` if none exists.
   */
  fun getActiveSession(userId: KUUID, refreshToken: String): ChonkitSession? {
    return dsl
      .selectFrom(CHONKIT_SESSIONS)
      .where(
        CHONKIT_SESSIONS.USER_ID.eq(userId)
          .and(CHONKIT_SESSIONS.REFRESH_TOKEN.eq(refreshToken))
          .and(CHONKIT_SESSIONS.EXPIRES_AT.gt(OffsetDateTime.now()))
      )
      .fetchOne()
      ?.toChonkitSession()
  }

  fun insertNewSession(
    userId: KUUID,
    refreshToken: String,
    keyName: String,
    keyVersion: String,
    expiresAt: OffsetDateTime,
  ): ChonkitSession? {
    return dsl
      .insertInto(CHONKIT_SESSIONS)
      .set(CHONKIT_SESSIONS.USER_ID, userId)
      .set(CHONKIT_SESSIONS.REFRESH_TOKEN, refreshToken)
      .set(CHONKIT_SESSIONS.KEY_NAME, keyName)
      .set(CHONKIT_SESSIONS.KEY_VERSION, keyVersion)
      .set(CHONKIT_SESSIONS.EXPIRES_AT, expiresAt)
      .returning()
      .fetchOne()
      ?.toChonkitSession()
  }

  fun getUser(userId: KUUID): User? {
    return dsl.selectFrom(USERS).where(USERS.ID.eq(userId)).fetchOne()?.toUser()
  }

  fun removeSingleSession(userId: KUUID, refreshToken: String): Int {
    return dsl
      .deleteFrom(CHONKIT_SESSIONS)
      .where(
        CHONKIT_SESSIONS.USER_ID.eq(userId).and(CHONKIT_SESSIONS.REFRESH_TOKEN.eq(refreshToken))
      )
      .execute()
  }

  /** Delete all sessions (refresh tokens) for the given user. */
  fun removeAllSessions(userId: KUUID): Int {
    return dsl.deleteFrom(CHONKIT_SESSIONS).where(CHONKIT_SESSIONS.USER_ID.eq(userId)).execute()
  }
}