/**
 * Application-wide settings constants and their default values.
 *
 * This file contains all the configuration keys and default values used throughout the application.
 * These settings can be overridden through the application's configuration settings.
 */
package net.barrage.llmao.core.administration.settings

/**
 * The main API for managing application settings.
 *
 * TODO: Remove SettingKey in favor of arbitrary keys.
 * TODO: Namespace settings and instantiate this per namespace.
 */
class Settings(private val repository: SettingsRepository) {
  /** Return all application settings, populating the list with any missing values with defaults. */
  suspend fun getAllWithDefaults(): ApplicationSettings {
    val settings = repository.listAll().toMutableList()

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
    return repository.getValue(key)
  }

  suspend fun update(updates: SettingsUpdate) {
    updates.validate()
    repository.update(updates)
  }
}
