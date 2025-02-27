package net.barrage.llmao.app.api.http.dto

import kotlinx.serialization.Serializable
import net.barrage.llmao.core.CharRange
import net.barrage.llmao.core.Validation

@Serializable
data class UpdateChatTitleDTO(@CharRange(min = 1, max = 255) var title: String) : Validation
