package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthKeyLocation
import io.github.smiley4.ktorswaggerui.data.AuthType
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

fun Application.configureOpenApi() {
  install(SwaggerUI) {
    info {
      title = "LLMAO API"
      version = "latest"
      description = "LLMAO API for development purposes."
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

fun Route.openApiRoutes() {
  route("openapi.json") { openApiSpec() }
  route("swagger-ui") { swaggerUI("/openapi.json") }
}
