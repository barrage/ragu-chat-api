package net.barrage.llmao.models

import kotlinx.serialization.Serializable

/** DTO for paginated lists. */
@Serializable data class CountedList<T>(val total: Int, val items: List<T>)
