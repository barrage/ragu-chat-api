package net.barrage.llmao.app.api.http.dto

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KUUID

/** DTO for session cookies. */
@Serializable data class SessionCookie(val id: KUUID) : Principal
