package net.barrage.llmao.app.api.http

import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.util.*
import java.time.Instant
import net.barrage.llmao.string

class CookieFactory(private val config: ApplicationConfig) {
  fun getRefreshTokenCookieName(): String {
    return config.string("chonkit.jwt.cookie.refresh.name")
  }

  fun createChonkitAccessTokenCookie(value: String): Cookie {
    return Cookie(
      name = config.string("chonkit.jwt.cookie.access.name"),
      value = value,
      encoding = CookieEncoding.RAW,
      maxAge = config.string("chonkit.jwt.accessTokenDurationSeconds").toInt(),
      domain = config.string("chonkit.jwt.cookie.access.domain"),
      path = "/",
      secure = config.string("chonkit.jwt.cookie.access.secure").toBoolean(),
      httpOnly = true,
      extensions = mapOf("SameSite" to config.string("chonkit.jwt.cookie.access.sameSite")),
    )
  }

  fun createChonkitRefreshTokenCookie(value: String): Cookie {
    return Cookie(
      name = config.string("chonkit.jwt.cookie.refresh.name"),
      value = value,
      encoding = CookieEncoding.RAW,
      maxAge = config.string("chonkit.jwt.refreshTokenDurationSeconds").toInt(),
      domain = config.string("chonkit.jwt.cookie.refresh.domain"),
      path = "/",
      secure = config.string("chonkit.jwt.cookie.refresh.secure").toBoolean(),
      httpOnly = true,
      extensions = mapOf("SameSite" to config.string("chonkit.jwt.cookie.refresh.sameSite")),
    )
  }

  fun createChonkitAccessTokenExpiryCookie(): Cookie {
    return Cookie(
      name = config.string("chonkit.jwt.cookie.access.name"),
      value = "",
      encoding = CookieEncoding.RAW,
      maxAge = 0,
      expires = Instant.now().toGMTDate(),
      domain = config.string("chonkit.jwt.cookie.access.domain"),
      path = "/",
      secure = config.string("chonkit.jwt.cookie.access.secure").toBoolean(),
      httpOnly = true,
      extensions = mapOf("SameSite" to config.string("chonkit.jwt.cookie.access.sameSite")),
    )
  }

  fun createChonkitRefreshTokenExpiryCookie(): Cookie {
    return Cookie(
      name = config.string("chonkit.jwt.cookie.refresh.name"),
      value = "",
      encoding = CookieEncoding.RAW,
      maxAge = 0,
      expires = Instant.now().toGMTDate(),
      domain = config.string("chonkit.jwt.cookie.refresh.domain"),
      path = "/",
      secure = config.string("chonkit.jwt.cookie.refresh.secure").toBoolean(),
      httpOnly = true,
      extensions = mapOf("SameSite" to config.string("chonkit.jwt.cookie.refresh.sameSite")),
    )
  }
}
