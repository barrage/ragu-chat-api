package net.barrage.llmao

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class ApplicationTest {
    @Test
    fun testRoot() = testApplication {
        client.get("/__health").apply {
            assertEquals(HttpStatusCode.OK, status)
            assert(bodyAsText().isEmpty())
        }
    }
}
