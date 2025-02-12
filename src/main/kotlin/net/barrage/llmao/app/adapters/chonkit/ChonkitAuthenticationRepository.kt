package net.barrage.llmao.app.adapters.chonkit

import java.time.OffsetDateTime
import kotlinx.coroutines.reactive.awaitFirstOrNull
import kotlinx.coroutines.reactive.awaitSingle
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.references.CHONKIT_SESSIONS
import org.jooq.DSLContext

class ChonkitAuthenticationRepository(private val dsl: DSLContext) {
  /**
   * Get an active session for the given user and refresh token.
   *
   * @param userId The user ID to search for.
   * @param refreshToken The refresh token to search for.
   * @return The active session, or `null` if none exists.
   */
  suspend fun getActiveSession(userId: KUUID, refreshToken: String): ChonkitSession? {
    return dsl
      .selectFrom(CHONKIT_SESSIONS)
      .where(
        CHONKIT_SESSIONS.USER_ID.eq(userId)
          .and(CHONKIT_SESSIONS.REFRESH_TOKEN.eq(refreshToken))
          .and(CHONKIT_SESSIONS.EXPIRES_AT.gt(OffsetDateTime.now()))
      )
      .awaitFirstOrNull()
      ?.toChonkitSession()
  }

  suspend fun insertNewSession(
    userId: KUUID,
    refreshToken: String,
    keyName: String,
    keyVersion: Int,
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
      .awaitSingle()
      ?.toChonkitSession()
  }

  suspend fun removeSingleSession(userId: KUUID, refreshToken: String): Int {
    return dsl
      .deleteFrom(CHONKIT_SESSIONS)
      .where(
        CHONKIT_SESSIONS.USER_ID.eq(userId).and(CHONKIT_SESSIONS.REFRESH_TOKEN.eq(refreshToken))
      )
      .awaitSingle()
  }

  /** Delete all sessions (refresh tokens) for the given user. */
  suspend fun removeAllSessions(userId: KUUID): Int {
    return dsl.deleteFrom(CHONKIT_SESSIONS).where(CHONKIT_SESSIONS.USER_ID.eq(userId)).awaitSingle()
  }
}
