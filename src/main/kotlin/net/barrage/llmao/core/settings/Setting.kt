package net.barrage.llmao.core.settings

import net.barrage.llmao.core.types.KOffsetDateTime
import net.barrage.llmao.tables.records.ApplicationSettingsRecord

data class Setting(
  val name: String,
  val value: String,
  val createdAt: KOffsetDateTime,
  val updatedAt: KOffsetDateTime,
)

fun ApplicationSettingsRecord.toModel(): Setting {
  return Setting(
    name = this.name,
    value = this.value,
    createdAt = this.createdAt!!,
    updatedAt = this.updatedAt!!,
  )
}
