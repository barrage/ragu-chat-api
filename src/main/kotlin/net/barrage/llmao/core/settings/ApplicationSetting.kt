package net.barrage.llmao.core.settings

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.tables.records.ApplicationSettingsRecord

@Serializable
data class ApplicationSetting(
  val name: SettingKey,
  val value: String,
  val createdAt: KOffsetDateTime? = null,
  val updatedAt: KOffsetDateTime? = null,
)

fun ApplicationSettingsRecord.toModel(): ApplicationSetting {
  return ApplicationSetting(
    name = SettingKey.tryFromString(this.name),
    value = this.value,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
  )
}
