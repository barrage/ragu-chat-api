import io.ktor.client.plugins.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.ErrorReason
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.IncomingSystemMessage
import net.barrage.llmao.core.workflow.OutgoingSystemMessage
import net.barrage.llmao.test.IntegrationTest
import net.barrage.llmao.test.adminWsSession
import net.barrage.llmao.test.decode
import net.barrage.llmao.test.openNewWorkflow
import net.barrage.llmao.test.sendClientSystem
import net.barrage.llmao.test.userWsSession
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class WebsocketIncomingSystemMessageTests : IntegrationTest(plugin = ChatPlugin()) {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration

  @BeforeAll
  fun setup() {
    runBlocking {
      agent = postgres.testAgent()
      agentConfiguration = postgres.testAgentConfiguration(agentId = agent.id)
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

    client.adminWsSession {
      openNewWorkflow(
        CHAT_WORKFLOW_ID,
        Json.encodeToJsonElement(NewChatWorkflowParameters(agent.id)),
      )
      send("asdf")
      val response = (incoming.receive() as Frame.Text).readText()
      val error = decode<AppError>(response)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.InvalidParameter, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingNewChatWorks() = wsTest { client ->
    var asserted = false

    client.adminWsSession {
      sendClientSystem(
        IncomingSystemMessage.CreateNewWorkflow(
          "CHAT",
          Json.encodeToJsonElement(NewChatWorkflowParameters(agent.id)),
        )
      )
      val response = (incoming.receive() as Frame.Text).readText()
      val message = decode<OutgoingSystemMessage.WorkflowOpen>(response)
      assertNotNull(message.id)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingNewChatWorksWithAlreadyOpenChat() = wsTest { client ->
    var asserted = false

    client.adminWsSession {
      val openChat =
        IncomingSystemMessage.CreateNewWorkflow(
          "CHAT",
          Json.encodeToJsonElement(NewChatWorkflowParameters(agent.id)),
        )

      sendClientSystem(openChat)
      val first = (incoming.receive() as Frame.Text).readText()
      val firstMessage = decode<OutgoingSystemMessage.WorkflowOpen>(first)
      assertNotNull(firstMessage.id)

      sendClientSystem(openChat)
      val second = (incoming.receive() as Frame.Text).readText()
      val secondMessage = decode<OutgoingSystemMessage.WorkflowOpen>(second)
      assertNotNull(secondMessage.id)

      assertNotEquals(firstMessage.id, secondMessage.id)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingExistingChatWorks() = wsTest { client ->
    var asserted = false

    client.adminWsSession {
      sendClientSystem(
        IncomingSystemMessage.CreateNewWorkflow(
          "CHAT",
          Json.encodeToJsonElement(NewChatWorkflowParameters(agent.id)),
        )
      )

      val first = (incoming.receive() as Frame.Text).readText()
      val firstMessage = decode<OutgoingSystemMessage.WorkflowOpen>(first)
      assertNotNull(firstMessage.id)

      sendClientSystem(
        IncomingSystemMessage.LoadExistingWorkflow(workflowType = "CHAT", firstMessage.id)
      )

      val second = (incoming.receive() as Frame.Text).readText()
      val secondMessage = decode<OutgoingSystemMessage.WorkflowOpen>(second)
      assertNotNull(secondMessage.id)

      assertEquals(firstMessage.id, secondMessage.id)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingExistingChatFailsDoesNotExist() = wsTest { client ->
    var asserted = false

    client.adminWsSession {
      val openChat =
        IncomingSystemMessage.LoadExistingWorkflow(workflowType = "CHAT", KUUID.randomUUID())
      sendClientSystem(openChat)

      val message = (incoming.receive() as Frame.Text).readText()
      val error = decode<AppError>(message)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun closesChannelOnCloseFrame() = wsTest { client ->
    var asserted = false

    client.adminWsSession {
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

    client.adminWsSession {
      val openChat =
        IncomingSystemMessage.CreateNewWorkflow(
          "CHAT",
          Json.encodeToJsonElement(NewChatWorkflowParameters(KUUID.randomUUID())),
        )
      sendClientSystem(openChat)

      val message = (incoming.receive() as Frame.Text).readText()
      val error = decode<AppError>(message)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }

  @Test
  fun openingChatFailsUserNotAllowedToAccessAgent() = wsTest { client ->
    val agent = postgres.testAgent(groups = listOf("admin"))
    postgres.testAgentConfiguration(agentId = agent.id)
    var asserted = false

    client.userWsSession {
      sendClientSystem(
        IncomingSystemMessage.CreateNewWorkflow(
          "CHAT",
          Json.encodeToJsonElement(NewChatWorkflowParameters(agent.id)),
        )
      )

      val message = (incoming.receive() as Frame.Text).readText()
      val error = decode<AppError>(message)
      assertEquals("API", error.errorType)
      assertEquals(ErrorReason.EntityDoesNotExist, error.errorReason)
      asserted = true
    }

    assert(asserted)
  }
}
