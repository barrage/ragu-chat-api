package net.barrage.llmao.app.adapters.chonkit.dto

import kotlinx.serialization.Serializable

/**
 * Used for refreshing Chonkit tokens, as well as logging the user out. Web clients do not need to
 * send this as their refresh token will be in a cookie.
 */
@Serializable data class ChonkitAuthenticationRequest(val refreshToken: String)
