package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminSettingsControllerTests : IntegrationTest() {
  private lateinit var userAdminSession: Session
  private lateinit var userAdmin: User

  @BeforeAll
  fun setup() {
    runBlocking {
      userAdmin = postgres.testUser(admin = true)
      userAdminSession = postgres.testSession(userAdmin.id)
    }
  }

  // Has to be run first since we assert it with defaults.
  @Test
  fun listsWildcardSettings() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val response =
      client.get("/admin/settings?setting=*") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<ApplicationSettings>()

    assertEquals(ApplicationSettings.defaults().chatMaxHistoryTokens, body.chatMaxHistoryTokens)
  }

  @Test
  fun shouldListSingleSetting() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val response =
      client.get("/admin/settings?setting=chatMaxHistoryTokens") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<ApplicationSettings>()

    assertEquals(ApplicationSettings.defaults().chatMaxHistoryTokens, body.chatMaxHistoryTokens)
  }

  @Test
  fun shouldUpdateSingleSetting() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val update = SettingsUpdate(listOf(SettingUpdate("chatMaxHistoryTokens", "15")))

    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val responseCheck =
      client.get("/admin/settings?setting=chatMaxHistoryTokens") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = responseCheck.body<ApplicationSettings>()

    assertEquals(15, body.chatMaxHistoryTokens)
  }

  @Test
  fun doesNotAllowChangingNonExistingSetting() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val update = SettingsUpdate(listOf(SettingUpdate("nonExistingSetting", "15")))

    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

  @Test
  fun doesNotAllowListingNonExistingSetting() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val response =
      client.get("/admin/settings?setting=nonExistingSetting") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
  }
}
