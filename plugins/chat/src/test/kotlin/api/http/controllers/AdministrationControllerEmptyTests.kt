
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import net.barrage.llmao.test.IntegrationTest
import net.barrage.llmao.test.adminAccessToken
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AdministrationControllerEmptyDBTests : IntegrationTest(plugin = ChatPlugin()) {
  @Test
  fun getAdminDashboardCounts() = test { client ->
    val response =
      client.get("/admin/dashboard/counts") { header(HttpHeaders.Cookie, adminAccessToken()) }

    assertEquals(200, response.status.value)
  }

  @Test
  fun getAdminDashboardChatHistory() = test { client ->
    val response =
      client.get("/admin/dashboard/chat/history?period=WEEK") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(HttpStatusCode.OK, response.status)
  }
}
