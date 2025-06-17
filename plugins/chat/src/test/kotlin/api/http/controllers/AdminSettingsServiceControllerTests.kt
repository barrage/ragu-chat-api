import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import net.barrage.llmao.core.settings.ApplicationSettings
import net.barrage.llmao.core.settings.SettingUpdate
import net.barrage.llmao.core.settings.SettingsUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AdminSettingsServiceControllerTests : IntegrationTest() {
  @Test
  fun updatesSingleSetting() = test { client ->
    val update = SettingsUpdate(listOf(SettingUpdate(MaxHistoryTokens.KEY, "15")))

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

    assertEquals(15, body[MaxHistoryTokens.KEY].toInt())
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
            SettingUpdate(MaxHistoryTokens.KEY, "15"),
            SettingUpdate(AgentPresencePenalty.KEY, "0.5"),
            SettingUpdate(AgentTitleMaxCompletionTokens.KEY, "10"),
            SettingUpdate(WhatsappAgentId.KEY, "00000000-0000-0000-0000-000000000000"),
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

    assertEquals(15, body[MaxHistoryTokens.KEY].toInt())
    assertEquals(0.5, body[AgentPresencePenalty.KEY].toDouble())
    assertEquals(10, body[AgentTitleMaxCompletionTokens.KEY].toInt())
    assertEquals("00000000-0000-0000-0000-000000000000", body[WhatsappAgentId.KEY])
  }

  @Test
  fun removesSettingWithNoDefault() = test { client ->
    val update =
      SettingsUpdate(
        updates =
          listOf(
            SettingUpdate(WhatsappAgentId.KEY, "00000000-0000-0000-0000-000000000000"),
            SettingUpdate(MaxHistoryTokens.KEY, "15"),
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

    assertEquals("00000000-0000-0000-0000-000000000000", updateCheck[WhatsappAgentId.KEY])
    assertEquals(15, updateCheck[MaxHistoryTokens.KEY].toInt())

    assertEquals(HttpStatusCode.NoContent, response.status)

    val remove = SettingsUpdate(removals = listOf(WhatsappAgentId.KEY, MaxHistoryTokens.KEY))

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

    assertNull(settings.getOptional(WhatsappAgentId.KEY))
    assertNull(settings.getOptional(MaxHistoryTokens.KEY))
  }
}
