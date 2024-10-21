package net.barrage.llmao.app.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason

@Serializable
enum class LoginSource {
  @SerialName("android") ANDROID,
  @SerialName("ios") IOS,
  @SerialName("web") WEB;

  override fun toString(): String {
    return super.name.lowercase()
  }

  companion object {
    fun tryFromString(value: String?): LoginSource {
      return when (value) {
        "android" -> ANDROID
        "ios" -> IOS
        "web" -> WEB
        else ->
          throw AppError.api(ErrorReason.InvalidParameter, "Unsupported login source '$value'")
      }
    }
  }
}
