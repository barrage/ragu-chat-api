package net.barrage.llmao.models

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.serializers.KUUID

/** DTO for session cookies. */
@Serializable data class UserSession(val id: KUUID) : Principal
