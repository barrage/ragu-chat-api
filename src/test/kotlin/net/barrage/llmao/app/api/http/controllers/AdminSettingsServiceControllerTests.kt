package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.SettingKey
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.SettingsUpdate
import net.barrage.llmao.sessionCookie
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminSettingsServiceControllerTests : IntegrationTest() {
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
  fun updatesSingleSetting() = test { client ->
    val update = SettingsUpdate(listOf(SettingUpdate(SettingKey.CHAT_MAX_HISTORY_TOKENS, "15")))

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

    assertEquals(15, body[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())
  }

  @Test
  fun doesNotAllowChangingNonExistingSetting() = test { client ->
    val update = """{"settings": "updates": [{"setting": "nonExistingSetting", "value": "15"}]}"""
    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

  @Test
  fun doesNotAllowRemovingNonExistingSetting() = test { client ->
    val update = """{"settings": "removals": ["nonExistingSetting"]}"""
    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.BadRequest, response.status)
  }

  @Test
  fun updatesAllSettingsAndListsAllCorrectly() = test { client ->
    val update =
      SettingsUpdate(
        updates =
          listOf(
            SettingUpdate(SettingKey.CHAT_MAX_HISTORY_TOKENS, "15"),
            SettingUpdate(SettingKey.AGENT_PRESENCE_PENALTY, "0.5"),
            SettingUpdate(SettingKey.AGENT_MAX_COMPLETION_TOKENS, "100"),
            SettingUpdate(SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS, "10"),
            SettingUpdate(SettingKey.WHATSAPP_AGENT_ID, "00000000-0000-0000-0000-000000000000"),
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
      client.get("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = responseCheck.body<ApplicationSettings>()

    assertEquals(15, body[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())
    assertEquals(0.5, body[SettingKey.AGENT_PRESENCE_PENALTY].toDouble())
    assertEquals(100, body[SettingKey.AGENT_MAX_COMPLETION_TOKENS].toInt())
    assertEquals(10, body[SettingKey.AGENT_TITLE_MAX_COMPLETION_TOKENS].toInt())
    assertEquals("00000000-0000-0000-0000-000000000000", body[SettingKey.WHATSAPP_AGENT_ID])
  }

  @Test
  fun removesSettingWithNoDefault() = test { client ->
    val update =
      SettingsUpdate(
        updates =
          listOf(
            SettingUpdate(SettingKey.WHATSAPP_AGENT_ID, "00000000-0000-0000-0000-000000000000"),
            SettingUpdate(SettingKey.CHAT_MAX_HISTORY_TOKENS, "15"),
          )
      )

    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    val updateCheck =
      client
        .get("/admin/settings") {
          header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        }
        .body<ApplicationSettings>()

    assertEquals("00000000-0000-0000-0000-000000000000", updateCheck[SettingKey.WHATSAPP_AGENT_ID])
    assertEquals(15, updateCheck[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())

    assertEquals(HttpStatusCode.OK, response.status)

    val remove =
      SettingsUpdate(
        removals = listOf(SettingKey.WHATSAPP_AGENT_ID, SettingKey.CHAT_MAX_HISTORY_TOKENS)
      )

    val responseRemove =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
        contentType(ContentType.Application.Json)
        setBody(remove)
      }

    assertEquals(HttpStatusCode.OK, responseRemove.status)

    val responseCheck =
      client.get("/admin/settings") {
        header(HttpHeaders.Cookie, sessionCookie(userAdminSession.sessionId))
      }

    assertEquals(HttpStatusCode.OK, responseCheck.status)

    val settings = responseCheck.body<ApplicationSettings>()

    assertNull(settings.getOptional(SettingKey.WHATSAPP_AGENT_ID))
    assertEquals(100_000, settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())
  }
}
