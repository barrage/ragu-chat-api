package net.barrage.llmao.dtos

import kotlinx.serialization.Serializable

@Serializable
data class PaginationInfo(
    val count: Int,
    val page: Int,
    val size: Int,
    val sortBy: String,
    val sortOrder: String
)
