package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
  val origins = environment.config.property("cors.origins").getList()
  val methods = environment.config.property("cors.methods").getList().map { HttpMethod(it) }
  val headers = environment.config.property("cors.headers").getList()
  val maxAgeInSeconds: Long = environment.config.property("cors.maxAge").getString().toLong()

  install(CORS) {
    for (origin in origins) {
      allowHost(origin, schemes = listOf("http", "https"))
    }
    this.methods.addAll(methods)
    this.headers.addAll(headers)
    this.maxAgeInSeconds = maxAgeInSeconds
  }
}
