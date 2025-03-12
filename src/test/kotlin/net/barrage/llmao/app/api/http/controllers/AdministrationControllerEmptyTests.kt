package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.adminAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdministrationControllerEmptyDBTests : IntegrationTest() {
  @Test
  fun getAdminDashboardCounts() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/counts") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)
  }

  @Test
  fun getAdminDashboardChatHistory() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=WEEK") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(HttpStatusCode.OK, response.status)
  }
}
