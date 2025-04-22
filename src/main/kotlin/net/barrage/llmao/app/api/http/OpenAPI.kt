package net.barrage.llmao.app.api.http

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.config.AuthKeyLocation
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.RequestConfig
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.route
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.github.smiley4.schemakenerator.reflection.ReflectionSteps.analyzeTypeUsingReflection
import io.github.smiley4.schemakenerator.reflection.ReflectionSteps.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.data.EnumConstType
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.SwaggerSteps.withTitle
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.ktor.server.application.*
import io.ktor.server.routing.Route
import net.barrage.llmao.types.KUUID

fun Route.openApiRoutes() {
  route("openapi.json") { openApi() }
  route("swagger-ui") { swaggerUI("/openapi.json") }
}

fun Application.configureOpenApi() {
  install(OpenApi) {
    info {
      title = "Ragu API"
      version = "latest"
      description = "Ragu API"
    }
    server {
      url = "http://localhost:42069"
      description = "Local Server"
    }
    server {
      url = "https://llmao-kotlin-api-development.barrage.dev"
      description = "Development Server"
    }
    security {
      securityScheme("jwt") {
        name = "access_token"
        type = AuthType.OAUTH2
        location = AuthKeyLocation.COOKIE
      }
      defaultSecuritySchemeNames = listOf("jwt")
    }
    schemas {
      generator = { type ->
        type
          .collectSubTypes(10)
          .analyzeTypeUsingReflection { enumConstType = EnumConstType.TO_STRING }
          .generateSwaggerSchema()
          .handleCoreAnnotations()
          .withTitle(TitleType.SIMPLE)
          .compileReferencingRoot()
      }
    }
  }
}

/** Utility for generating OpenAPI spec for query param pagination. */
fun RequestConfig.queryPaginationSort() {
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

fun RequestConfig.queryPagination() {
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

fun RequestConfig.queryListChatsFilters() {
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

fun RequestConfig.queryListAgentsFilters() {
  queryParameter<String>("name") {
    description = "Filter by name"
    required = false
  }
  queryParameter<Boolean>("active") {
    description = "Filter by active status"
    required = false
  }
}
