package net.barrage.llmao.core

import io.ktor.server.config.ApplicationConfig

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
