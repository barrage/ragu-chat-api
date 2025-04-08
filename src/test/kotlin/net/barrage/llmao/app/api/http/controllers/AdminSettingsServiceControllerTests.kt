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
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.core.model.ApplicationSettings
import net.barrage.llmao.core.model.SettingKey
import net.barrage.llmao.core.model.SettingUpdate
import net.barrage.llmao.core.model.SettingsUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdminSettingsServiceControllerTests : IntegrationTest() {
  @Test
  fun updatesSingleSetting() = test { client ->
    val update = SettingsUpdate(listOf(SettingUpdate(SettingKey.CHAT_MAX_HISTORY_TOKENS, "15")))

    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.NoContent, response.status)

    val responseCheck =
      client.get("/admin/settings?setting=chatMaxHistoryTokens") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(HttpStatusCode.OK, responseCheck.status)

    val body = responseCheck.body<ApplicationSettings>()

    assertEquals(15, body[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())
  }

  @Test
  fun gracefullyHandlesUpdateOfNonExistingSetting() = test { client ->
    val update = """{"updates": [{"key": "nonExistingSetting", "value": "15"}]}"""
    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.NoContent, response.status)
  }

  @Test
  fun gracefullyHandlesRemovalOfNonExistingSetting() = test { client ->
    val update = """{"removals": ["nonExistingSetting"]}"""
    val response =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.NoContent, response.status)
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
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    assertEquals(HttpStatusCode.NoContent, response.status)

    val responseCheck =
      client.get("/admin/settings") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, responseCheck.status)

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
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(update)
      }

    val updateCheck =
      client
        .get("/admin/settings") { header(HttpHeaders.Cookie, adminAccessToken()) }
        .body<ApplicationSettings>()

    assertEquals("00000000-0000-0000-0000-000000000000", updateCheck[SettingKey.WHATSAPP_AGENT_ID])
    assertEquals(15, updateCheck[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())

    assertEquals(HttpStatusCode.NoContent, response.status)

    val remove =
      SettingsUpdate(
        removals = listOf(SettingKey.WHATSAPP_AGENT_ID, SettingKey.CHAT_MAX_HISTORY_TOKENS)
      )

    val responseRemove =
      client.put("/admin/settings") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(remove)
      }

    assertEquals(HttpStatusCode.NoContent, responseRemove.status)

    val responseCheck =
      client.get("/admin/settings") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(HttpStatusCode.OK, responseCheck.status)

    val settings = responseCheck.body<ApplicationSettings>()

    assertNull(settings.getOptional(SettingKey.WHATSAPP_AGENT_ID))
    assertEquals(100_000, settings[SettingKey.CHAT_MAX_HISTORY_TOKENS].toInt())
  }
}
