package net.barrage.llmao.app.api.http

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthKeyLocation
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.dsl.routes.OpenApiRequest
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.github.smiley4.schemakenerator.core.annotations.Format
import io.github.smiley4.schemakenerator.core.data.AnnotationData
import io.github.smiley4.schemakenerator.core.data.PrimitiveTypeData
import io.github.smiley4.schemakenerator.core.data.TypeId
import io.github.smiley4.schemakenerator.reflection.collectSubTypes
import io.github.smiley4.schemakenerator.reflection.data.EnumConstType
import io.github.smiley4.schemakenerator.reflection.processReflection
import io.github.smiley4.schemakenerator.swagger.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.data.SwaggerTypeHint
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.github.smiley4.schemakenerator.swagger.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.handleCoreAnnotations
import io.github.smiley4.schemakenerator.swagger.handleSwaggerAnnotations
import io.github.smiley4.schemakenerator.swagger.withTitle
import io.ktor.server.application.*
import io.ktor.server.routing.*
import java.time.OffsetDateTime
import java.util.*
import net.barrage.llmao.core.models.common.Role
import net.barrage.llmao.core.types.KUUID

fun Route.openApiRoutes() {
  route("openapi.json") { openApiSpec() }
  route("swagger-ui") { swaggerUI("/openapi.json") }
}

fun Application.configureOpenApi() {
  install(SwaggerUI) {
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
      securityScheme("cookieAuth") {
        name = environment.config.property("cookies.session.cookieName").getString()
        type = AuthType.API_KEY
        location = AuthKeyLocation.COOKIE
      }
      defaultSecuritySchemeNames = listOf("cookieAuth")
    }
    schemas {
      generator = {
        it
          .collectSubTypes(10)
          .processReflection {
            enumConstType = EnumConstType.TO_STRING

            customProcessor<UUID> {
              PrimitiveTypeData(
                id = TypeId.build(UUID::class.qualifiedName!!),
                simpleName = UUID::class.simpleName!!,
                qualifiedName = UUID::class.qualifiedName!!,
                annotations =
                  mutableListOf(
                    AnnotationData(
                      name = SwaggerTypeHint::class.qualifiedName!!,
                      values = mutableMapOf("type" to "string"),
                    ),
                    AnnotationData(
                      name = Format::class.qualifiedName!!,
                      values = mutableMapOf("format" to "uuid"),
                      annotation = Format("uuid"),
                    ),
                  ),
              )
            }

            customProcessor<OffsetDateTime> {
              PrimitiveTypeData(
                id = TypeId.build(OffsetDateTime::class.qualifiedName!!),
                simpleName = OffsetDateTime::class.simpleName!!,
                qualifiedName = OffsetDateTime::class.qualifiedName!!,
                annotations =
                  mutableListOf(
                    AnnotationData(
                      name = SwaggerTypeHint::class.qualifiedName!!,
                      values = mutableMapOf("type" to "string"),
                      annotation = SwaggerTypeHint("string"),
                    ),
                    AnnotationData(
                      name = Format::class.qualifiedName!!,
                      values = mutableMapOf("format" to "date-time"),
                      annotation = Format("date-time"),
                    ),
                  ),
              )
            }
          }
          .generateSwaggerSchema()
          .handleCoreAnnotations()
          .handleSwaggerAnnotations()
          .withTitle(TitleType.SIMPLE)
          .compileReferencingRoot()
      }
    }
  }
}

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
