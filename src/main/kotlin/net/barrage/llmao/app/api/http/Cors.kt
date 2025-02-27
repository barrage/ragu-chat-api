package net.barrage.llmao.app.api.http

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
  val origins = environment.config.property("cors.origins").getList()
  val methods = environment.config.property("cors.methods").getList().map { HttpMethod(it) }
  val headers = environment.config.property("cors.headers").getList()

  install(CORS) {
    this.allowCredentials = true
    this.allowOrigins { origins.contains(it) }
    this.methods.addAll(methods)
    this.headers.addAll(headers)
    this.allowNonSimpleContentTypes = true
  }
}
