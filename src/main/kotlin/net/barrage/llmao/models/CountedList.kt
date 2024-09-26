package net.barrage.llmao.models

import kotlinx.serialization.Serializable

@Serializable data class CountedList<T>(val total: Int, val items: List<T>)
