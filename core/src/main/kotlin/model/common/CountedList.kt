package net.barrage.llmao.core.model.common

import kotlinx.serialization.Serializable

/** DTO for paginated lists. */
@Serializable
data class CountedList<T>(val total: Int, val items: List<T>) {
    fun <O> map(transform: (T) -> O): CountedList<O> {
        return CountedList(total, items.map(transform))
    }
}
