package net.barrage.llmao.app.api.http

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRequest

/** Utility for generating OpenAPI spec for query param pagination. */
fun OpenApiRequest.queryPaginationSort() {
  queryParameter<Int>("page") {
    description = "Page number for pagination"
    required = false
    example("default") { value = 1 }
  }
  queryParameter<Int>("perPage") {
    description = "Number of items per page"
    required = false
    example("default") { value = 10 }
  }
  queryParameter<String>("sortBy") {
    description = "Sort by field"
    required = false
    example("default") { value = "name" }
  }
  queryParameter<String>("sortOrder") {
    description = "Sort order (asc or desc)"
    required = false
    example("default") { value = "asc" }
  }
}

fun OpenApiRequest.queryPagination() {
  queryParameter<Int>("page") {
    description = "Page number for pagination"
    required = false
    example("default") { value = 1 }
  }
  queryParameter<Int>("perPage") {
    description = "Number of items per page"
    required = false
    example("default") { value = 10 }
  }
}
