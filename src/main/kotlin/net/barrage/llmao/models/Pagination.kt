package net.barrage.llmao.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.barrage.llmao.plugins.QueryParameter

/** Basic pagination parameters. */
@Serializable
data class Pagination(
  @QueryParameter private var page: Int?,
  @QueryParameter private var perPage: Int?,
) {
  constructor() : this(1, 25)

  fun limitOffset(): Pair<Int, Int> {
    val perPage = perPage ?: 25
    val page = page ?: 1
    return Pair(perPage, (page - 1) * perPage)
  }
}

/** Determines how list items are ordered. */
@Serializable
enum class SortOrder {
  @SerialName("asc") ASC,
  @SerialName("desc") DESC,
}

/** Pagination parameters with sorting options. */
@Serializable
data class PaginationSort(
  @QueryParameter private var page: Int?,
  @QueryParameter private var perPage: Int?,
  @QueryParameter private var sortBy: String?,
  @QueryParameter private var sortOrder: SortOrder?,
) {

  constructor() : this(1, 25, null, SortOrder.ASC)

  fun limitOffset(): Pair<Int, Int> {
    val perPage = perPage ?: 25
    val page = page ?: 1
    return Pair(perPage, (page - 1) * perPage)
  }

  fun sorting(): Pair<String?, SortOrder> {
    return Pair(sortBy, sortOrder ?: SortOrder.ASC)
  }
}
