import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.chat.ChatPlugin
import net.barrage.llmao.chat.model.Agent
import net.barrage.llmao.chat.model.AgentConfiguration
import net.barrage.llmao.chat.model.Chat
import net.barrage.llmao.chat.model.ChatWithMessages
import net.barrage.llmao.chat.whatsapp.model.WhatsAppNumber
import net.barrage.llmao.core.input.whatsapp.model.Contact
import net.barrage.llmao.core.input.whatsapp.model.InfobipResponse
import net.barrage.llmao.core.input.whatsapp.model.InfobipResult
import net.barrage.llmao.core.input.whatsapp.model.Message
import net.barrage.llmao.core.input.whatsapp.model.Price
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.test.IntegrationTest
import net.barrage.llmao.test.USER_USER
import net.barrage.llmao.test.userAccessToken
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class WhatsAppControllerTests :
  IntegrationTest(useWeaviate = true, enableWhatsApp = true, plugin = ChatPlugin()) {
  private lateinit var whatsAppNumber: WhatsAppNumber
  private lateinit var whatsAppAgentOne: Agent
  private lateinit var whatsAppAgentTwo: Agent
  private lateinit var whatsAppAgentOneConfiguration: AgentConfiguration
  private lateinit var whatsAppAgentTwoConfiguration: AgentConfiguration
  private lateinit var whatsAppChat: Chat
  private lateinit var whatsAppMessageOne: MessageGroupAggregate
  private lateinit var whatsAppMessageTwo: MessageGroupAggregate

  private val userNumber = "385981234567"

  @BeforeAll
  fun setup() {
    runBlocking { weaviate!!.insertTestCollection("Kusturica") }
  }

  @BeforeEach
  fun setupWhatsAppUser() {
    runBlocking {
      whatsAppNumber = postgres.testWhatsAppNumber(USER_USER, "385981234567")

      whatsAppAgentOne = postgres.testAgent("Test Whatsapp Agent 1")
      whatsAppAgentOneConfiguration =
        postgres.testAgentConfiguration(
          whatsAppAgentOne.id,
          context = "WhatsApp Test Agent Context",
        )

      whatsAppAgentTwo = postgres.testAgent("Test Whatsapp Agent 2")
      whatsAppAgentTwoConfiguration =
        postgres.testAgentConfiguration(
          whatsAppAgentTwo.id,
          context = "WhatsApp Test Agent Context",
        )

      whatsAppChat =
        postgres.testChat(
          USER_USER,
          whatsAppAgentOne.id,
          whatsAppAgentOneConfiguration.id,
          type = "WHATSAPP",
        )

      whatsAppMessageOne =
        postgres.testMessagePair(whatsAppChat.id, "First Message", "First Response")

      whatsAppMessageTwo =
        postgres.testMessagePair(whatsAppChat.id, "Second Message", "Second Response")

      postgres.setWhatsAppAgent(whatsAppAgentOne.id)
    }

    @AfterEach
    fun tearDown() {
      runBlocking {
        postgres.deleteTestChat(whatsAppChat.id)
        postgres.deleteTestWhatsAppNumber(whatsAppNumber.id)
        postgres.deleteWhatsAppAgent()
      }
    }

    /** Webhook tests */
    @Test
    fun webhookIncomingMessage() = test { client ->
      val infobipResponse =
        InfobipResponse(
          results =
            listOf(
              InfobipResult(
                from = userNumber,
                to = "385912222222",
                integrationType = "WHATSAPP",
                receivedAt = "2024-11-28T07:57:28.000+00:00",
                messageId = "wamid.HBgLMzg1OTg2MzE4MjkVAgASGBQzQUIxNzY4MzZBOUE2MDE0QUZDMwA=",
                pairedMessageId = null,
                callbackData = null,
                message = Message.Text(text = "Hello"),
                contact = Contact(name = "385981234567"),
                price = Price(pricePerMessage = 0.0, currency = "EUR"),
              )
            ),
          messageCount = 1,
          pendingMessageCount = 0,
        )

      val response =
        client.post("/net/barrage/llmao/chat/whatsapp/webhook") {
          header("Content-Type", "application/json")
          setBody(infobipResponse)
        }

      assertEquals(200, response.status.value)
    }

    /** User tests */
    @Test
    fun getWhatsAppNumbersForUser() = test { client ->
      val response =
        client.get("/net/barrage/llmao/chat/whatsapp/numbers") {
          header("Cookie", userAccessToken())
        }

      assertEquals(200, response.status.value)
      val body = response.body<List<WhatsAppNumber>>()
      assertEquals(1, body.size)
      assertEquals("385981234567", body[0].phoneNumber)
      assertEquals(USER_USER.id, body[0].userId)
    }

    @Test
    fun addWhatsAppNumberForUser() = test { client ->
      val response =
        client.post("/net/barrage/llmao/chat/whatsapp/numbers") {
          header("Cookie", userAccessToken())
          header("Content-Type", "application/json")
          setBody(mapOf("phoneNumber" to "385981234565"))
        }

      assertEquals(200, response.status.value)
      val body = response.body<WhatsAppNumber>()
      assertEquals("385981234565", body.phoneNumber)
      assertEquals(USER_USER.id, body.userId)

      postgres.deleteTestWhatsAppNumber(body.id)
    }

    @Test
    fun updateWhatsAppNumberForUser() = test { client ->
      val response =
        client.put("/whatsapp/numbers/${whatsAppNumber.id}") {
          header("Cookie", userAccessToken())
          header("Content-Type", "application/json")
          setBody(mapOf("phoneNumber" to "385981234564"))
        }

      assertEquals(200, response.status.value)
      val body = response.body<WhatsAppNumber>()
      assertEquals("385981234564", body.phoneNumber)
      assertEquals(USER_USER.id, body.userId)
    }

    @Test
    fun deleteWhatsAppNumberForUser() = test { client ->
      val response =
        client.delete("/whatsapp/numbers/${whatsAppNumber.id}") {
          header("Cookie", userAccessToken())
        }

      assertEquals(204, response.status.value)
    }

    @Test
    fun getWhatsAppChatsForUser() = test { client ->
      val response =
        client.get("/net/barrage/llmao/chat/whatsapp/chats") { header("Cookie", userAccessToken()) }

      assertEquals(200, response.status.value)
      val body = response.body<ChatWithMessages>()
      assertEquals(whatsAppChat.id, body.chat.id)
      assertEquals(USER_USER.id, body.chat.userId)
      assertEquals(2, body.messages.items.size)
      //      assertEquals(whatsAppMessageOne.id, body.messages[1].id)
      //      assertEquals("user", body.messages[1].senderType)
      //      assertEquals(whatsAppMessageTwo.id, body.messages[0].id)
      //      assertEquals("assistant", body.messages[0].senderType)
      //      assertEquals(whatsAppMessageOne.id, body.messages[0].responseTo)
    }
  }
}
