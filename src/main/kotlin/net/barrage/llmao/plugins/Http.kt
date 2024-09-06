package net.barrage.llmao.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.*

fun Application.configureCors() {
    val origins: List<String> =
        environment.config.propertyOrNull("ktor.cors.origins")?.getString()?.split(" ") ?: emptyList()
    val methods: List<HttpMethod> =
        environment.config.propertyOrNull("ktor.cors.methods")?.getString()?.split(" ")?.map { HttpMethod(it) }
            ?: emptyList()
    val headers: List<String> =
        environment.config.propertyOrNull("ktor.cors.headers")?.getString()?.split(" ") ?: emptyList()
    val maxAgeInSeconds: Long =
        environment.config.propertyOrNull("ktor.cors.maxAge")?.getString()?.toLong() ?: 0

    install(CORS) {
        this.allowOrigins { origins.contains(it) }
        this.methods.addAll(methods)
        this.headers.addAll(headers)
        this.maxAgeInSeconds = maxAgeInSeconds
    }
}