package net.barrage.llmao

import io.ktor.client.request.*
import kotlin.test.Test
import kotlin.test.assertEquals

class HealthCheckTest : IntegrationTest(usePostgres = false) {
  @Test
  fun testHealthCheck() = test {
    val res = client.get("__health")
    assertEquals(200, res.status.value)
  }
}
