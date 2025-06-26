package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig
import io.ktor.util.logging.KtorSimpleLogger
import io.ktor.util.logging.Logger
import kotlin.reflect.KClass
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

/**
 * Creates a logger with a name based on the calling class.
 *
 * @param clazz The class to use for naming the logger
 * @return A KtorSimpleLogger with a name based on the class
 */
fun logger(clazz: KClass<*>): Logger {
  return KtorSimpleLogger(clazz.qualifiedName ?: clazz.java.name)
}
