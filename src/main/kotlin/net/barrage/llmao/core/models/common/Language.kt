package net.barrage.llmao.core.models.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.error.apiError

@Serializable
enum class Language(val language: String) {
  @SerialName("cro") CRO("cro"),
  @SerialName("eng") ENG("eng");

  companion object {
    fun tryFromString(value: String): Language {
      return when (value) {
        "cro" -> CRO
        "eng" -> ENG
        else -> throw apiError("Unsupported language", "Language '$value' not supported")
      }
    }
  }
}
