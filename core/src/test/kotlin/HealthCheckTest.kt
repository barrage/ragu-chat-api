import io.ktor.client.request.get
import net.barrage.llmao.test.IntegrationTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HealthCheckTest : IntegrationTest() {
  @Test
  fun testHealthCheck() = test {
    val res = client.get("__health")
    assertEquals(200, res.status.value)
  }
}
