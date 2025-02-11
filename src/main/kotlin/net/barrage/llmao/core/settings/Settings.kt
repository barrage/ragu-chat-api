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
 * Maximum number of tokens to keep in chat histories. Used to prevent context windows from growing
 * too large. Always applies to all chats.
 */
internal const val GLOBAL_MAX_HISTORY_TOKENS = "chatMaxHistoryTokens"

/** Default value for [GLOBAL_MAX_HISTORY_TOKENS]. */
internal const val DEFAULT_GLOBAL_MAX_HISTORY_TOKENS = 100_000

/**
 * Maximum number of tokens to generate on completions. The global value applies to all agents,
 * unless overridden by their configuration.
 */
internal const val GLOBAL_MAX_COMPLETION_TOKENS = "agentMaxCompletionTokens"

/** The maximum amount of tokens for title generation. Applied to all agents. */
internal const val GLOBAL_MAX_TITLE_COMPLETION_TOKENS = "agentTitleMaxCompletionTokens"

/** Default value for [GLOBAL_MAX_TITLE_COMPLETION_TOKENS]. */
internal const val DEFAULT_GLOBAL_MAX_TITLE_COMPLETION_TOKENS = 100

/** The maximum amount of tokens for summary generation. Applied to all agents. */
internal const val GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS = "agentSummaryMaxCompletionTokens"

/** Default value for [GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS]. */
internal const val DEFAULT_GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS = 10_000

/**
 * Used to penalize tokens that are already present in the context window. The global value applies
 * to all agents, unless overridden by their configuration.
 */
internal const val GLOBAL_PRESENCE_PENALTY = "agentPresencePenalty"

/** The default value for [GLOBAL_PRESENCE_PENALTY]. */
internal const val DEFAULT_GLOBAL_PRESENCE_PENALTY = 0.0

/** The main API for managing application settings. */
class Settings(private val settingsRepository: SettingsRepository) {
  /** Return all application settings with defaults. */
  suspend fun getAllWithDefaults(): ApplicationSettingsDefault {
    val settings = settingsRepository.listAll()
    return ApplicationSettingsDefault.fromSettings(ApplicationSettings.fromList(settings))
  }

  /** Return only the specified application settings, leaving everything else null. */
  suspend fun get(keys: List<String>): ApplicationSettings {
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

/** Holds all configurable application settings with non-null values. */
@Serializable
data class ApplicationSettingsDefault(
  val chatMaxHistoryTokens: Int = DEFAULT_GLOBAL_MAX_HISTORY_TOKENS,
  val presencePenalty: Double = DEFAULT_GLOBAL_PRESENCE_PENALTY,
  val maxCompletionTokens: Int? = null,
  val titleMaxCompletionTokens: Int = DEFAULT_GLOBAL_MAX_TITLE_COMPLETION_TOKENS,
  val summaryMaxCompletionTokens: Int = DEFAULT_GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS,
) {
  companion object {
    /** Helper to populate user-set fields in the application settings. */
    internal fun fromSettings(settings: ApplicationSettings): ApplicationSettingsDefault {
      return ApplicationSettingsDefault(
        chatMaxHistoryTokens = settings.chatMaxHistoryTokens ?: DEFAULT_GLOBAL_MAX_HISTORY_TOKENS,
        presencePenalty = settings.presencePenalty ?: DEFAULT_GLOBAL_PRESENCE_PENALTY,
        maxCompletionTokens = settings.maxCompletionTokens,
        titleMaxCompletionTokens =
          settings.titleMaxCompletionTokens ?: DEFAULT_GLOBAL_MAX_TITLE_COMPLETION_TOKENS,
        summaryMaxCompletionTokens =
          settings.summaryMaxCompletionTokens ?: DEFAULT_GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS,
      )
    }
  }
}

/** Holds all configurable application settings. */
@Serializable
data class ApplicationSettings(
  var chatMaxHistoryTokens: Int? = null,
  var presencePenalty: Double? = null,
  var maxCompletionTokens: Int? = null,
  var titleMaxCompletionTokens: Int? = null,
  var summaryMaxCompletionTokens: Int? = null,
) {
  companion object {
    internal fun fromList(settings: List<Setting>): ApplicationSettings {
      val out = ApplicationSettings()

      for (setting in settings) {
        when (setting.name) {
          GLOBAL_MAX_HISTORY_TOKENS -> out.chatMaxHistoryTokens = setting.value.toInt()
          GLOBAL_PRESENCE_PENALTY -> out.presencePenalty = setting.value.toDouble()
          GLOBAL_MAX_COMPLETION_TOKENS -> out.maxCompletionTokens = setting.value.toInt()
          GLOBAL_MAX_TITLE_COMPLETION_TOKENS -> out.titleMaxCompletionTokens = setting.value.toInt()
          GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS ->
            out.summaryMaxCompletionTokens = setting.value.toInt()
        }
      }

      return out
    }
  }
}

private val ALLOWED_KEYS =
  listOf(
    GLOBAL_MAX_HISTORY_TOKENS,
    GLOBAL_PRESENCE_PENALTY,
    GLOBAL_MAX_COMPLETION_TOKENS,
    GLOBAL_MAX_TITLE_COMPLETION_TOKENS,
    GLOBAL_MAX_SUMMARY_COMPLETION_TOKENS,
  )

/** DTO for updating multiple settings at once. */
@Serializable
data class SettingsUpdate(val updates: List<SettingUpdate>) {
  fun validate() {
    for (update in updates) {
      if (!ALLOWED_KEYS.contains(update.key)) {
        throw AppError.api(ErrorReason.InvalidParameter, "Invalid setting key: ${update.key}")
      }
      if (update.value.isBlank()) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Invalid setting (${update.key}) value: ${update.value}",
        )
      }
    }
  }
}

/** DTO for updating a single setting. */
@Serializable data class SettingUpdate(val key: String, val value: String)
