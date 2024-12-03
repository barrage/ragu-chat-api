package net.barrage.llmao.adapters.chonkit

import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.tables.records.ChonkitSessionsRecord

data class ChonkitSession(
  val userId: KUUID,
  val refreshToken: String,
  val keyName: String,
  val keyVersion: String,
  val expiresAt: KOffsetDateTime,
  val createdAt: KOffsetDateTime,
)

fun ChonkitSessionsRecord.toChonkitSession() =
  ChonkitSession(
    userId = this.userId,
    refreshToken = this.refreshToken,
    keyName = this.keyName,
    keyVersion = this.keyVersion,
    expiresAt = this.expiresAt,
    createdAt = this.createdAt!!,
  )
