package net.barrage.llmao.core.administration.settings

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.tables.records.ApplicationSettingsRecord
import net.barrage.llmao.types.KOffsetDateTime

/** The ID of the agent used for WhatsApp. */
data object WhatsappAgentId {
  const val KEY = "WHATSAPP.AGENT_ID"
}

/** The maximum amount of tokens for completion generation for the WhatsApp agent. */
data object WhatsappMaxCompletionTokens {
  const val KEY = "WHATSAPP.AGENT_MAX_COMPLETION_TOKENS"
  const val DEFAULT = 300
}

@JvmInline
@Serializable
value class ApplicationSettings(private val m: Map<String, ApplicationSetting>) {
  operator fun get(key: String): String {
    if (!m.containsKey(key))
      throw AppError.api(
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
@Serializable data class SettingUpdate(val key: String, val value: String)
