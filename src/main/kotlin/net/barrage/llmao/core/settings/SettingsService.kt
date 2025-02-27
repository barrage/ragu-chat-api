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

/** The main API for managing application settings. */
class SettingsService(private val settingsRepository: SettingsRepository) {
  /** Return all application settings, populating the list with any missing values with defaults. */
  suspend fun getAllWithDefaults(): ApplicationSettings {
    val settings = settingsRepository.listAll().toMutableList()

    DefaultSetting.entries.forEach { default ->
      if (settings.none { it.name == default.setting.name }) {
        settings.add(default.setting)
      }
    }

    val appSettings =
      settings.fold(mutableMapOf<SettingKey, ApplicationSetting>()) { acc, setting ->
        acc[setting.name] = setting
        acc
      }

    return ApplicationSettings(appSettings)
  }

  suspend fun get(key: SettingKey): String? {
    return settingsRepository.getValue(key)
  }

  suspend fun update(updates: SettingsUpdate) {
    updates.validate()
    settingsRepository.update(updates)
  }
}

enum class SettingKey {
  /**
   * Maximum number of tokens to keep in chat histories. Used to prevent context windows from
   * growing too large. Always applies to all chats.
   *
   * DEFAULT: [DefaultSetting.CHAT_MAX_HISTORY_TOKENS]
   */
  CHAT_MAX_HISTORY_TOKENS,

  /**
   * Used to penalize tokens that are already present in the context window. The global value
   * applies to all agents, unless overridden by their configuration.
   *
   * DEFAULT: [DefaultSetting.AGENT_PRESENCE_PENALTY]
   */
  AGENT_PRESENCE_PENALTY,

  /**
   * The maximum amount of tokens for title generation. Applied to all agents.
   *
   * DEFAULT: [DefaultSetting.AGENT_TITLE_MAX_COMPLETION_TOKENS]
   */
  AGENT_TITLE_MAX_COMPLETION_TOKENS,

  /**
   * The maximum amount of tokens for summary generation. Applied to all agents.
   *
   * DEFAULT: [DefaultSetting.AGENT_SUMMARY_MAX_COMPLETION_TOKENS]
   */
  AGENT_SUMMARY_MAX_COMPLETION_TOKENS,

  /**
   * Maximum number of tokens to generate on completions. The global value applies to all agents,
   * unless overridden by their configuration.
   *
   * DEFAULT: -
   */
  AGENT_MAX_COMPLETION_TOKENS,

  /**
   * The ID of the agent used for WhatsApp.
   *
   * DEFAULT: -
   */
  WHATSAPP_AGENT_ID,

  /**
   * The maximum amount of tokens for completion generation for the WhatsApp agent.
   *
   * DEFAULT: [DefaultSetting.WHATSAPP_AGENT_MAX_COMPLETION_TOKENS]
   */
  WHATSAPP_AGENT_MAX_COMPLETION_TOKENS,

  /**
   * The LLM provider to use for JiraKira.
   *
   * DEFAULT: [DefaultSetting.JIRA_KIRA_LLM_PROVIDER]
   */
  JIRA_KIRA_LLM_PROVIDER,

  /**
   * Which model will be used for JiraKira. Has to be compatible with [JIRA_KIRA_LLM_PROVIDER].
   *
   * DEFAULT: [DefaultSetting.JIRA_KIRA_MODEL]
   */
  JIRA_KIRA_MODEL,

  /**
   * The attribute to use as the time slot attribute when creating worklog entries with the Jira
   * API. Defined in Jira.
   *
   * DEFAULT: -
   */
  JIRA_TIME_SLOT_ATTRIBUTE_KEY;

  companion object {
    fun tryFromString(value: String): SettingKey {
      try {
        return valueOf(value)
      } catch (_: Exception) {
        throw AppError.api(ErrorReason.InvalidParameter, "Invalid setting key: $value")
      }
    }
  }
}

/**
 * A thin wrapper around a map of application settings to provide a little bit more type safety than
 * we would get with a regular map.
 *
 * If a [SettingKey] is specified in [DefaultSetting], then this map is guaranteed to contain either
 * the configured or the default value.
 *
 * If a [SettingKey] is not specified in [DefaultSetting], the map is not guaranteed to contain a
 * value and should *always* be obtained with [getOptional].
 */
@JvmInline
@Serializable
value class ApplicationSettings(private val m: Map<SettingKey, ApplicationSetting>) {
  operator fun get(key: SettingKey): String {
    return m[key]!!.value
  }

  fun getOptional(key: SettingKey): String? {
    return m[key]?.value
  }
}

enum class DefaultSetting(val setting: ApplicationSetting) {
  CHAT_MAX_HISTORY_TOKENS(
    ApplicationSetting(SettingKey.CHAT_MAX_HISTORY_TOKENS, 100_000.toString())
  ),
  AGENT_PRESENCE_PENALTY(ApplicationSetting(SettingKey.AGENT_PRESENCE_PENALTY, 0.0.toString())),
  AGENT_TITLE_MAX_COMPLETION_TOKENS(
    ApplicationSetting(SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS, 100.toString())
  ),
  AGENT_SUMMARY_MAX_COMPLETION_TOKENS(
    ApplicationSetting(SettingKey.AGENT_SUMMARY_MAX_COMPLETION_TOKENS, 10_000.toString())
  ),
  WHATSAPP_AGENT_MAX_COMPLETION_TOKENS(
    ApplicationSetting(SettingKey.WHATSAPP_AGENT_MAX_COMPLETION_TOKENS, 135.toString())
  ),
  JIRA_KIRA_LLM_PROVIDER(ApplicationSetting(SettingKey.JIRA_KIRA_LLM_PROVIDER, "openai")),
  JIRA_KIRA_MODEL(ApplicationSetting(SettingKey.JIRA_KIRA_MODEL, "gpt-4o-mini")),
}

/** DTO for updating multiple settings at once. */
@Serializable
data class SettingsUpdate(
  val updates: List<SettingUpdate>? = null,
  val removals: List<SettingKey>? = null,
) {
  fun validate() {
    updates?.let {
      for (update in updates) {
        if (update.value.isBlank()) {
          throw AppError.api(
            ErrorReason.InvalidParameter,
            "Invalid setting '${update.key}'; value cannot be blank",
          )
        }
      }
    }
  }
}

/** DTO for updating a single setting. */
@Serializable data class SettingUpdate(val key: SettingKey, val value: String)
