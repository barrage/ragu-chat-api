/** API for managing application and plugin settings. */
package net.barrage.llmao.core.settings

import net.barrage.llmao.core.repository.SettingsRepository

/** The main API for managing application settings. */
class Settings(private val repository: SettingsRepository) {
  /** Return all registered application settings. */
  suspend fun getAll(): ApplicationSettings {
    val settings = repository.listAll().toMutableList()

    val appSettings =
      settings.fold(mutableMapOf<String, ApplicationSetting>()) { acc, setting ->
        acc[setting.name] = setting
        acc
      }

    return ApplicationSettings(appSettings)
  }

  suspend fun get(key: String): String? {
    return repository.getValue(key)
  }

  suspend fun update(updates: SettingsUpdate) {
    updates.validate()
    repository.update(updates)
  }
}
