package net.barrage.llmao.app.api.http

import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRequest
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KUUID

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

fun OpenApiRequest.queryListChatsFilters() {
  queryParameter<KUUID>("userId") {
    description = "Filter by user ID"
    required = false
  }
  queryParameter<KUUID>("agentId") {
    description = "Filter by agent ID"
    required = false
  }
  queryParameter<String>("title") {
    description = "Filter by chat title"
    required = false
  }
}

fun OpenApiRequest.queryListUsersFilters() {
  queryParameter<String>("name") {
    description = "Filter by name"
    required = false
  }
  queryParameter<Role>("role") {
    description = "Filter by role"
    required = false
  }
  queryParameter<Boolean>("active") {
    description = "Filter by active status"
    required = false
  }
}

fun OpenApiRequest.queryListAgentsFilters() {
  queryParameter<String>("name") {
    description = "Filter by name"
    required = false
  }
  queryParameter<Boolean>("active") {
    description = "Filter by active status"
    required = false
  }
}
