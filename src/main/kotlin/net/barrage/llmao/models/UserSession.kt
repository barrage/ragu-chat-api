package net.barrage.llmao.models

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KUUID

@Serializable
data class UserSession(
    val id: KUUID,
    val source: String? = null,
    val app: String? = null,
) : Principal
