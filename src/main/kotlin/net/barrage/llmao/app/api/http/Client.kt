package net.barrage.llmao.app.api.http

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/** Initialize Ktor's [HttpClient] with JSON content negotiation. TODO: Move to app */
fun httpClient(): HttpClient {
  return HttpClient(Apache) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  }
}
