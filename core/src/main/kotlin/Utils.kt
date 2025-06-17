package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import net.barrage.llmao.core.types.KUUID

/** Shorthand for `config.property(key).getString()` */
fun ApplicationConfig.string(key: String): String {
  return property(key).getString()
}

/** Shorthand for `config.property(key).getString().toLong` */
fun ApplicationConfig.long(key: String): Long {
  return property(key).getString().toLong()
}

/** Shorthand for `config.property(key).getString().toInt` */
fun ApplicationConfig.int(key: String): Int {
  return property(key).getString().toInt()
}

/** Attempt to parse the given string to an UUID, throwing an [AppError] if it fails. */
fun tryUuid(value: String): KUUID {
  return try {
    KUUID.fromString(value)
  } catch (e: IllegalArgumentException) {
    throw AppError.api(ErrorReason.InvalidParameter, "'$value' is not a valid UUID", original = e)
  }
}
