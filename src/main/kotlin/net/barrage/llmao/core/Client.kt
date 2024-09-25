package net.barrage.llmao.core

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/** Initialize Ktor's [HttpClient] with JSON content negotiation. */
fun httpClient(): HttpClient {
  return HttpClient(Apache) {
    install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
  }
}
