package net.barrage.llmao.app.api.http.dto

import kotlinx.serialization.Serializable
import net.barrage.llmao.utils.CharRange
import net.barrage.llmao.utils.Validation

@Serializable
data class UpdateChatTitleDTO(@CharRange(min = 3, max = 255) var title: String) : Validation
