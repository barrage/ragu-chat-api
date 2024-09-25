package net.barrage.llmao.plugins

import io.github.smiley4.ktorswaggerui.SwaggerUI
import io.github.smiley4.ktorswaggerui.routing.openApiSpec
import io.github.smiley4.ktorswaggerui.routing.swaggerUI
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
      url = "http://guja:42069" // TODO: change to actual url
      description = "Development Server"
    }
  }
}

fun Route.openApiRoutes() {
  route("openapi.json") { openApiSpec() }
  route("swagger-ui") { swaggerUI("/openapi.json") }
}
