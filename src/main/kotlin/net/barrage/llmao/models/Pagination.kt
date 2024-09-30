package net.barrage.llmao.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Basic pagination parameters. */
@Serializable
data class Pagination(private val page: Int, private val perPage: Int) {
  fun limitOffset(): Pair<Int, Int> {
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
  private val page: Int = 1,
  private val perPage: Int = 25,
  private val sortBy: String?,
  private val sortOrder: SortOrder = SortOrder.ASC,
) {
  companion object {
    fun default(): PaginationSort {
      return PaginationSort(1, 25, null, SortOrder.ASC)
    }
  }

  fun limitOffset(): Pair<Int, Int> {
    return Pair(perPage, (page - 1) * perPage)
  }

  fun sorting(): Pair<String?, SortOrder> {
    return Pair(sortBy, sortOrder)
  }
}
