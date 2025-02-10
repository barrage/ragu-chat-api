/**
 * Application-wide settings constants and their default values.
 *
 * This file contains all the configuration keys and default values used throughout the application.
 * These settings can be overridden through the application's configuration settings.
 */
package net.barrage.llmao.core.settings

import kotlinx.serialization.Serializable
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

/**
 * Maximum number of tokens to keep in chat history. Used to prevent context windows from growing
 * too large.
 */
internal const val CHAT_MAX_HISTORY_TOKENS = "chatMaxHistoryTokens"

/** Default value for [CHAT_MAX_HISTORY_TOKENS]. */
internal const val DEFAULT_CHAT_MAX_HISTORY_TOKENS = 100_000

/** The main API for managing application settings. */
class Settings(private val settingsRepository: SettingsRepository) {
  suspend fun list(keys: List<String>): ApplicationSettings {
    val settings =
      if (keys.size == 1 && keys[0] == "*") {
        settingsRepository.listAll()
      } else {
        for (key in keys) {
          if (!ALLOWED_KEYS.contains(key)) {
            throw AppError.api(ErrorReason.InvalidParameter, "Invalid setting key: $key")
          }
        }
        settingsRepository.list(keys)
      }

    return ApplicationSettings.fromList(settings)
  }

  suspend fun update(updates: SettingsUpdate) {
    updates.validate()
    settingsRepository.update(updates)
  }
}

/** Holds all configurable application settings. */
@Serializable
data class ApplicationSettings(var chatMaxHistoryTokens: Int? = DEFAULT_CHAT_MAX_HISTORY_TOKENS) {
  companion object {
    fun fromList(settings: List<Setting>): ApplicationSettings {
      val out = ApplicationSettings()

      for (setting in settings) {
        when (setting.name) {
          CHAT_MAX_HISTORY_TOKENS -> out.chatMaxHistoryTokens = setting.value.toInt()
        }
      }

      return out
    }

    fun defaults(): ApplicationSettings {
      return ApplicationSettings()
    }
  }
}

private val ALLOWED_KEYS = listOf(CHAT_MAX_HISTORY_TOKENS)

/** DTO for updating multiple settings at once. */
@Serializable
data class SettingsUpdate(val updates: List<SettingUpdate>) {
  fun validate() {
    for (update in updates) {
      if (!ALLOWED_KEYS.contains(update.key)) {
        throw AppError.api(ErrorReason.InvalidParameter, "Invalid setting key: ${update.key}")
      }
    }
  }
}

/** DTO for updating a single setting. */
@Serializable data class SettingUpdate(val key: String, val value: String)
