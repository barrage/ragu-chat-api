package net.barrage.llmao.models

import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KOffsetDateTime
import net.barrage.llmao.serializers.KUUID
import net.barrage.llmao.tables.records.SessionsRecord

@Serializable
data class SessionData(
    val sessionId: KUUID,
    val userId: KUUID,
    val createdAt: KOffsetDateTime,
    val updatedAt: KOffsetDateTime,
    val expiresAt: KOffsetDateTime
) {
    fun isValid(): Boolean {
        return expiresAt.isAfter(KOffsetDateTime.now())
    }
}

fun SessionsRecord.toSessionData() = SessionData(
    sessionId = this.id!!,
    userId = this.userId!!,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
    expiresAt = this.expiresAt!!
)