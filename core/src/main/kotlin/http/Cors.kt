package net.barrage.llmao.core.http

import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS

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
