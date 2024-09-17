package net.barrage.llmao.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class LoginSource {
    @SerialName("android")
    ANDROID,

    @SerialName("ios")
    IOS,

    @SerialName("web")
    WEB;

    companion object {
        fun fromString(value: String): LoginSource {
            return when (value) {
                "android" -> ANDROID
                "ios" -> IOS
                else -> WEB
            }
        }
    }
}