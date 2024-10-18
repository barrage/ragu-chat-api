package net.barrage.llmao

import io.ktor.client.request.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthCheckTest : IntegrationTest(false, false) {

  @Test
  fun testHealthCheck() = test {
    val res = client.get("__health")
    assertEquals(200, res.status.value)
  }
}
