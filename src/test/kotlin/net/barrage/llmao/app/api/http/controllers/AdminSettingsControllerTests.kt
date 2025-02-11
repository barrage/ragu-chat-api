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
import net.barrage.llmao.app.api.http.dto.ApplicationSettingsResponse
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.settings.ApplicationSettingsDefault
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

    val body = responseCheck.body<ApplicationSettingsResponse>()

    assertEquals(ApplicationSettingsDefault(), body.defaults)
    assertEquals(15, body.configured.chatMaxHistoryTokens)
    assertNull(body.configured.presencePenalty)
    assertNull(body.configured.maxCompletionTokens)
    assertNull(body.configured.titleMaxCompletionTokens)
    assertNull(body.configured.summaryMaxCompletionTokens)
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

  @Test
  fun updatesAllSettingsAndListsWithWildcard() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val update =
      SettingsUpdate(
        listOf(
          SettingUpdate("chatMaxHistoryTokens", "15"),
          SettingUpdate("agentPresencePenalty", "0.5"),
          SettingUpdate("agentMaxCompletionTokens", "100"),
          SettingUpdate("agentTitleMaxCompletionTokens", "10"),
          SettingUpdate("agentSummaryMaxCompletionTokens", "10"),
        )
      )

    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val responseCheck =
      client.get("/admin/settings?setting=*") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = responseCheck.body<ApplicationSettingsResponse>()

    assertEquals(ApplicationSettingsDefault(), body.defaults)
    assertEquals(15, body.configured.chatMaxHistoryTokens)
    assertEquals(0.5, body.configured.presencePenalty)
    assertEquals(100, body.configured.maxCompletionTokens)
    assertEquals(10, body.configured.titleMaxCompletionTokens)
    assertEquals(10, body.configured.summaryMaxCompletionTokens)
  }

  @Test
  fun listsSpecificSettings() = test {
    val client = createClient { install(ContentNegotiation) { json() } }

    val update =
      SettingsUpdate(
        listOf(
          SettingUpdate("agentChatMaxHistoryTokens", "15"),
          SettingUpdate("agentPresencePenalty", "0.5"),
        )
      )
    client.put("/admin/settings") {
      header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      contentType(ContentType.Application.Json)
      setBody(update)
    }

    val response =
      client.get("/admin/settings?setting=chatMaxHistoryTokens&setting=agentPresencePenalty") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<ApplicationSettingsResponse>()

    assertEquals(ApplicationSettingsDefault(), body.defaults)
    assertEquals(15, body.configured.chatMaxHistoryTokens)
    assertEquals(0.5, body.configured.presencePenalty)
    assertNull(body.configured.maxCompletionTokens)
    assertNull(body.configured.titleMaxCompletionTokens)
    assertNull(body.configured.summaryMaxCompletionTokens)
  }
}
