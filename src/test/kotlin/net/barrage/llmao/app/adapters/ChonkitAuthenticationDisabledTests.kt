package net.barrage.llmao.app.adapters

import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.serialization.kotlinx.json.*
import net.barrage.llmao.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChonkitAuthenticationDisabledTests : IntegrationTest() {
  @Test
  fun routesAreDisabledWhenFeatureIsDisabled() = test {
    val client = createClient { install(ContentNegotiation) { json() } }
    val tokenRes = client.post("/auth/chonkit/token")
    assertEquals(404, tokenRes.status.value)

    val refreshRes = client.post("/auth/chonkit/refresh")
    assertEquals(404, refreshRes.status.value)

    val logoutRes = client.post("/auth/chonkit/logout")
    assertEquals(404, logoutRes.status.value)
  }
}
