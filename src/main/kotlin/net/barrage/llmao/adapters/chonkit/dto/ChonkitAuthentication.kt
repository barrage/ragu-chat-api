package net.barrage.llmao.adapters.chonkit.dto

import kotlinx.serialization.Serializable

/**
 * Obtained from calling
 * [authenticate][net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService.authenticate].
 */
@Serializable data class ChonkitAuthentication(val accessToken: String, val refreshToken: String)
