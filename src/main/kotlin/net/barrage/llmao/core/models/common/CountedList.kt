package net.barrage.llmao.core.models.common

import kotlinx.serialization.Serializable

/** DTO for paginated lists. */
@Serializable data class CountedList<T>(val total: Int, val items: List<T>)
