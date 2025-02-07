package net.barrage.llmao.app.api.ws

import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.chatSession
import net.barrage.llmao.core.models.Agent
import net.barrage.llmao.core.models.AgentConfiguration
import net.barrage.llmao.core.models.Session
import net.barrage.llmao.core.models.User
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.error.AppError
import net.barrage.llmao.error.ErrorReason
import net.barrage.llmao.receiveJson
import net.barrage.llmao.sendClientSystem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WebsocketIncomingSystemMessageTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var user: User
  private lateinit var session: Session

  @BeforeAll
  fun setup() {
    runBlocking {
      agent = postgres.testAgent()
      agentConfiguration = postgres.testAgentConfiguration(agentId = agent.id)
      user = postgres.testUser(email = "not@important.org", admin = false)
      session = postgres.testSession(user.id)
    }
  }

  @Test
  fun rejectsRequestNoSession() = wsTest { client ->
    var asserted = false

    client.webSocket("/") {
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      val err = closeReason.await()!!.message
      assertEquals("Unauthorized", err)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun rejectsRequestMalformedToken() = wsTest { client ->
    var asserted = false

    client.webSocket("/?token=foo") {
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      val err = closeReason.await()!!.message
      assertEquals("Unauthorized", err)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun rejectsRequestInvalidToken() = wsTest { client ->
    var asserted = false

    val token = KUUID.randomUUID()

    client.webSocket("/?token=$token") {
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      val err = closeReason.await()!!.message
      assertEquals("Unauthorized", err)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun rejectsMessageInvalidJson() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      send("asdf")
      val response = (incoming.receive() as Frame.Text).readText()
      val error = receiveJson<AppError>(response)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.InvalidParameter, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingNewChatWorks() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      sendClientSystem(IncomingSystemMessage.CreateNewWorkflow(agent.id))
      val response = (incoming.receive() as Frame.Text).readText()
      val message = receiveJson<OutgoingSystemMessage.WorkflowOpen>(response)
      assertNotNull(message.id)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingNewChatWorksWithAlreadyOpenChat() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      val openChat = IncomingSystemMessage.CreateNewWorkflow(agent.id)

      sendClientSystem(openChat)
      val first = (incoming.receive() as Frame.Text).readText()
      val firstMessage = receiveJson<OutgoingSystemMessage.WorkflowOpen>(first)
      assertNotNull(firstMessage.id)

      sendClientSystem(openChat)
      val second = (incoming.receive() as Frame.Text).readText()
      val secondMessage = receiveJson<OutgoingSystemMessage.WorkflowOpen>(second)
      assertNotNull(secondMessage.id)

      assertNotEquals(firstMessage.id, secondMessage.id)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingExistingChatWorks() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      sendClientSystem(IncomingSystemMessage.CreateNewWorkflow(agent.id))

      val first = (incoming.receive() as Frame.Text).readText()
      val firstMessage = receiveJson<OutgoingSystemMessage.WorkflowOpen>(first)
      assertNotNull(firstMessage.id)

      sendClientSystem(IncomingSystemMessage.LoadExistingWorkflow(firstMessage.id))

      val second = (incoming.receive() as Frame.Text).readText()
      val secondMessage = receiveJson<OutgoingSystemMessage.WorkflowOpen>(second)
      assertNotNull(secondMessage.id)

      assertEquals(firstMessage.id, secondMessage.id)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingExistingChatFailsDoesNotExist() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      val openChat = IncomingSystemMessage.LoadExistingWorkflow(KUUID.randomUUID())
      sendClientSystem(openChat)

      val message = (incoming.receive() as Frame.Text).readText()
      val error = receiveJson<AppError>(message)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun closesChannelOnCloseFrame() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      send(Frame.Close())
      val result = incoming.receiveCatching()
      assert(result.isClosed)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingChatFailsAgentDoesNotExist() = wsTest { client ->
    var asserted = false

    client.chatSession(session.sessionId) {
      val openChat = IncomingSystemMessage.CreateNewWorkflow(KUUID.randomUUID())
      sendClientSystem(openChat)

      val message = (incoming.receive() as Frame.Text).readText()
      val error = receiveJson<AppError>(message)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }
}
