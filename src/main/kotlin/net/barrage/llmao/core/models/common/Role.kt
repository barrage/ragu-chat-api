package net.barrage.llmao.core.models.common

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Role {
  @SerialName("admin") ADMIN,
  @SerialName("user") USER,
}
