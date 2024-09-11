package net.barrage.llmao.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Languages(val language: String) {
    @SerialName("cro")
    CRO("Croatian"),

    @SerialName("eng")
    ENG("English"),;
}