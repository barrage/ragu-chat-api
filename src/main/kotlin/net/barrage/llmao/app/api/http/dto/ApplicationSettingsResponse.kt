package net.barrage.llmao.app.api.http.dto

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.ApplicationSettingsDefault

@Serializable
data class ApplicationSettingsResponse(
  val configured: ApplicationSettings,
  val defaults: ApplicationSettingsDefault = ApplicationSettingsDefault(),
)
