package net.barrage.llmao.core.administration.settings

import io.ktor.util.logging.KtorSimpleLogger
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.jsonObject
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.tables.records.ApplicationSettingsRecord
import net.barrage.llmao.types.KOffsetDateTime

@Serializable
data class ApplicationSetting(
  val name: SettingKey,
  val value: String,
  val createdAt: KOffsetDateTime? = null,
  val updatedAt: KOffsetDateTime? = null,
)

fun ApplicationSettingsRecord.toModel(): ApplicationSetting {
  return ApplicationSetting(
    name = SettingKey.Companion.tryFromString(this.name),
    value = this.value,
    createdAt = this.createdAt,
    updatedAt = this.updatedAt,
  )
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

@Serializable
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
      } catch (e: Exception) {
        throw AppError.api(
          ErrorReason.InvalidParameter,
          "Invalid setting key: $value",
          original = e,
        )
      }
    }
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
  WHATSAPP_AGENT_MAX_COMPLETION_TOKENS(
    ApplicationSetting(SettingKey.WHATSAPP_AGENT_MAX_COMPLETION_TOKENS, 135.toString())
  ),
  JIRA_KIRA_LLM_PROVIDER(ApplicationSetting(SettingKey.JIRA_KIRA_LLM_PROVIDER, "openai")),
  JIRA_KIRA_MODEL(ApplicationSetting(SettingKey.JIRA_KIRA_MODEL, "gpt-4o-mini")),
}

/** DTO for updating multiple settings at once. */
@Serializable(with = SettingsUpdateSerializer::class)
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

object SettingsUpdateSerializer : KSerializer<SettingsUpdate> {
  @Serializable data class SettingUpdateTemp(val key: String, val value: String)

  private val log = KtorSimpleLogger("net.barrage.llmao.SettingsUpdateSerializer")

  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("SettingsUpdate") {
      element("updates", ListSerializer(SettingUpdate.serializer()).descriptor, isOptional = true)
      element("removals", ListSerializer(SettingKey.serializer()).descriptor, isOptional = true)
    }

  override fun serialize(encoder: Encoder, value: SettingsUpdate) {
    encoder.encodeStructure(descriptor) {
      value.updates?.let {
        encodeSerializableElement(descriptor, 0, ListSerializer(SettingUpdate.serializer()), it)
      }
      value.removals?.let {
        encodeSerializableElement(descriptor, 1, ListSerializer(SettingKey.serializer()), it)
      }
    }
  }

  override fun deserialize(decoder: Decoder): SettingsUpdate {
    var updates = mutableListOf<SettingUpdate>()
    var removals = mutableListOf<SettingKey>()

    val jsonDecoder = decoder as? JsonDecoder ?: error("Only JSON is supported")

    val element = jsonDecoder.decodeJsonElement().jsonObject

    element["updates"]?.let {
      val u =
        jsonDecoder.json.decodeFromJsonElement(ListSerializer(SettingUpdateTemp.serializer()), it)
      for (update in u) {
        try {
          updates.add(SettingUpdate(SettingKey.tryFromString(update.key), update.value))
        } catch (e: AppError) {
          log.warn("Skipping invalid key in `updates`: {}", e.message)
        }
      }
    }

    element["removals"]?.let {
      val r = jsonDecoder.json.decodeFromJsonElement(ListSerializer(String.serializer()), it)
      for (key in r) {
        try {
          removals.add(SettingKey.tryFromString(key))
        } catch (e: AppError) {
          log.warn("Skipping invalid key in `removals`: {}", e.message)
        }
      }
    }

    return SettingsUpdate(updates, removals)
  }
}

/** DTO for updating a single setting. */
@Serializable data class SettingUpdate(val key: SettingKey, val value: String)
