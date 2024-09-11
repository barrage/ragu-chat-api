package net.barrage.llmao.enums

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Roles {
    @SerialName("admin")
    ADMIN,

    @SerialName("user")
    USER;
}
