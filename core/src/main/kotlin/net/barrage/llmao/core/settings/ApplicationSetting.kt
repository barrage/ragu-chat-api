package net.barrage.llmao.core.settings

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.tables.records.ApplicationSettingsRecord
import net.barrage.llmao.core.types.KOffsetDateTime

@JvmInline
@Serializable
value class ApplicationSettings(private val m: Map<String, ApplicationSetting>) {
  operator fun get(key: String): String {
    if (!m.containsKey(key))
      throw AppError.Companion.api(
        ErrorReason.InvalidParameter,
        "Missing setting '$key'; If the setting is optional, use `getOptional` instead.",
      )
    return m[key]!!.value
  }

  fun getOptional(key: String): String? {
    return m[key]?.value
  }
}

@Serializable
data class ApplicationSetting(
  val name: String,
  val value: String,
  val createdAt: KOffsetDateTime? = null,
  val updatedAt: KOffsetDateTime? = null,
)

fun ApplicationSettingsRecord.toModel(): ApplicationSetting {
  return ApplicationSetting(
    name = this.name,
    value = this.value,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
  )
}

/** DTO for updating multiple settings at once. */
@Serializable
data class SettingsUpdate(
  val updates: List<SettingUpdate>? = null,
  val removals: List<String>? = null,
) {
  fun validate() {
    updates?.let {
      for (update in updates) {
        if (update.value.isBlank()) {
          throw AppError.Companion.api(
            ErrorReason.InvalidParameter,
            "Invalid setting '${update.key}'; value cannot be blank",
          )
        }
      }
    }
  }
}

/** DTO for updating a single setting. */
@Serializable data class SettingUpdate(val key: String, val value: String)
