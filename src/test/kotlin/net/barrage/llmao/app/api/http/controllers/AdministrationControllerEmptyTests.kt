package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdministrationControllerEmptyDBTests : IntegrationTest() {
  private lateinit var admin: User
  private lateinit var adminSession: Session

  @BeforeAll
  fun setup() {
    admin = postgres!!.testUser(admin = true, active = true, email = "admin@barrage.net")
    adminSession = postgres!!.testSession(admin.id)
  }

  @Test
  fun getAdminDashboardCounts() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/counts") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(200, response.status.value)
  }

  @Test
  fun getAdminDashboardChatHistory() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/dashboard/chat/history?period=WEEK") {
        header(HttpHeaders.Cookie, sessionCookie(adminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)
  }
}
