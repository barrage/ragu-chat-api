package net.barrage.llmao

import io.ktor.http.*
import io.ktor.test.dispatcher.*
import junit.framework.TestCase.assertTrue
import kotlin.test.*
import net.barrage.llmao.core.models.Session

class ApplicationTest : LlmaoTestClass() {
  lateinit var session: Session

  @BeforeTest
  fun setup() {
    val user = createUser(true)
    session = createSession(user.id)
  }

  @AfterTest
  fun tearDown() {
    cleanseTables()
  }

  @Test
  fun testHealthCheck() =
    testSuspend(timeoutMillis = 5000) {
      with(engine) {
        handleRequest {
            uri = "/__health"
            method = HttpMethod.Get
            addHeader("Cookie", sessionCookie(session.sessionId))
          }
          .response
          .apply {
            assertEquals(HttpStatusCode.OK, status())
            assertTrue(content.isNullOrEmpty())
            assertNotNull(headers["Set-Cookie"])
          }
      }
    }
}
