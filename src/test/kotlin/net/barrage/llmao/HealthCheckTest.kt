package net.barrage.llmao

import io.ktor.client.request.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthCheckTest {

  @Test
  fun testHealthCheck() = testApplication {
    val res = client.get("__health")
    assertEquals(200, res.status.value)
  }
}
