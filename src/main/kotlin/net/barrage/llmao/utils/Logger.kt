package net.barrage.llmao.utils

import org.slf4j.Logger
import org.slf4j.LoggerFactory

object Logger {
  private val logger: Logger = LoggerFactory.getLogger("GlobalLogger")

  fun info(message: String) {
    logger.info(message)
  }

  fun debug(message: String) {
    logger.debug(message)
  }

  fun warn(message: String) {
    logger.warn(message)
  }

  fun trace(message: String) {
    logger.trace(message)
  }

  fun error(message: String, throwable: Throwable? = null) {
    logger.error(message, throwable)
  }
}
