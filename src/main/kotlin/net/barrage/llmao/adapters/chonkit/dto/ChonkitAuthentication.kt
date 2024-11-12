package net.barrage.llmao.adapters.chonkit.dto

import io.ktor.server.auth.*
import kotlinx.serialization.Serializable

/**
 * Obtained from calling
 * [authenticate][net.barrage.llmao.adapters.chonkit.ChonkitAuthenticationService.authenticate].
 */
@Serializable data class ChonkitAuthentication(val accessToken: String, val refreshToken: String)

/** Access token cookie issued on successful chonkit logins/refreshes. */
data class ChonkitAccessTokenCookie(val token: String) : Principal {
  companion object {
    fun from(auth: ChonkitAuthentication): ChonkitAccessTokenCookie {
      return ChonkitAccessTokenCookie(auth.accessToken)
    }
  }
}

/** Refresh token cookie issued on successful chonkit logins/refreshes. */
data class ChonkitRefreshTokenCookie(val token: String) : Principal {
  companion object {
    fun from(auth: ChonkitAuthentication): ChonkitRefreshTokenCookie {
      return ChonkitRefreshTokenCookie(auth.refreshToken)
    }
  }
}
