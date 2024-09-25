package net.barrage.llmao.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.error.apiError

@Serializable
enum class LoginSource {
  @SerialName("android") ANDROID,
  @SerialName("ios") IOS,
  @SerialName("web") WEB;

  companion object {
    fun tryFromString(value: String?): LoginSource {
      return when (value) {
        "android" -> ANDROID
        "ios" -> IOS
        "web" -> WEB
        else -> throw apiError("Validation", "Unsupported login source")
      }
    }
  }
}
