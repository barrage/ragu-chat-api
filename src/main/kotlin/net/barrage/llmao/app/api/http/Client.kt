package net.barrage.llmao.app.api.http

import io.ktor.client.*
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

/** Initialize Ktor's [HttpClient] with JSON content negotiation. TODO: Move to app */
fun httpClient(): HttpClient {
  return HttpClient(CIO) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
}
