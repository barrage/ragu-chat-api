package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.data.AuthKeyLocation
import io.github.smiley4.ktorswaggerui.data.AuthType
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
import io.github.smiley4.schemakenerator.reflection.data.EnumConstType
import io.github.smiley4.schemakenerator.reflection.processReflection
import io.github.smiley4.schemakenerator.swagger.compileReferencingRoot
import io.github.smiley4.schemakenerator.swagger.data.RefType
import io.github.smiley4.schemakenerator.swagger.data.TitleType
import io.github.smiley4.schemakenerator.swagger.generateSwaggerSchema
import io.github.smiley4.schemakenerator.swagger.withTitle
import io.ktor.server.application.*
import io.ktor.server.routing.*

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
      url = "https://llmao-kotlin-api-staging.m2.barrage.beer"
      description = "Development Server"
    }
    security {
      securityScheme("cookieAuth") {
        name = environment.config.property("session.cookieName").getString()
        type = AuthType.API_KEY
        location = AuthKeyLocation.COOKIE
      }
      defaultSecuritySchemeNames = listOf("cookieAuth")
    }
    schemas {
      generator = {
        it
          .processReflection { enumConstType = EnumConstType.TO_STRING }
          .generateSwaggerSchema()
          .withTitle(TitleType.SIMPLE)
          .compileReferencingRoot(RefType.SIMPLE)
      }
    }
  }
}

fun Route.openApiRoutes() {
  route("openapi.json") { openApiSpec() }
  route("swagger-ui") { swaggerUI("/openapi.json") }
}
