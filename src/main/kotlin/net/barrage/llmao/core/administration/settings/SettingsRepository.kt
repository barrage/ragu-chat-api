package net.barrage.llmao.core.administration.settings

interface SettingsRepository {
  suspend fun getValue(key: SettingKey): String?

  suspend fun listAll(): List<ApplicationSetting>

  suspend fun list(keys: List<String>): List<ApplicationSetting>

  suspend fun update(update: SettingsUpdate)
}
