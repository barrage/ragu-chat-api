import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.core.ValidationError
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PropertyUpdate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class ChatControllerTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chatOne: Chat
  private lateinit var chatTwo: Chat
  private lateinit var messageGroupOne: MessageGroupAggregate
  private lateinit var messageGroupTwo: MessageGroupAggregate

  @BeforeAll
  fun setup() {
    runBlocking {
      agent = postgres.testAgent(active = true)
      agentConfiguration = postgres.testAgentConfiguration(agent.id)

      chatOne = postgres.testChat(USER_USER, agent.id, agentConfiguration.id)
      chatTwo = postgres.testChat(USER_USER, agent.id, agentConfiguration.id)

      messageGroupOne = postgres.testMessagePair(chatOne.id, "First Message", "First Response")
      messageGroupTwo = postgres.testMessagePair(chatOne.id, "Second Message", "Second Response")
    }
  }

  @Test
  fun shouldRetrieveAllChatsDefaultPagination() = test { client ->
    val response = client.get("/chats") { header("Cookie", userAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Chat>>()
    assertEquals(2, body.total)
    assertEquals(chatOne.id, body.items[0].id)
    assertEquals(chatTwo.id, body.items[1].id)
  }

  @Test
  fun shouldRetrieveAllChatsSortedByCreatedAt() = test { client ->
    val response =
      client.get("/chats?sortBy=createdAt&sortOrder=desc") { header("Cookie", userAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<CountedList<Chat>>()
    assertEquals(2, body.total)
    assertEquals(chatOne.id, body.items[1].id)
    assertEquals(chatTwo.id, body.items[0].id)
  }

  @Test
  fun shouldUpdateChatTitle() = test { client ->
    val updatedTitle = UpdateChatTitleDTO("Updated Title")
    val response =
      client.put("/chats/${chatOne.id}") {
        header("Cookie", userAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updatedTitle)
      }

    assertEquals(200, response.status.value)
    val body = response.body<Chat>()
    assertEquals(chatOne.id, body.id)
    assertEquals("Updated Title", body.title)
  }

  @Test
  fun shouldUpdateChatTitleValidationFails() = test { client ->
    val updatedTitle = UpdateChatTitleDTO("")
    val response =
      client.put("/chats/${chatOne.id}") {
        header("Cookie", userAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updatedTitle)
      }

    assertEquals(422, response.status.value)
    val body = response.body<List<ValidationError>>()
    assertEquals("title", body[0].fieldName)
  }

  @Test
  fun shouldDeleteChat() = test { client ->
    val chatDelete = postgres.testChat(USER_USER, agent.id, agentConfiguration.id)
    val response = client.delete("/chats/${chatDelete.id}") { header("Cookie", userAccessToken()) }

    assertEquals(204, response.status.value)
    val body = response.body<String>()
    assertEquals("", body)

    val check = client.get("/chats/${chatDelete.id}") { header("Cookie", userAccessToken()) }

    assertEquals(404, check.status.value)
  }

  @Test
  fun shouldRetrieveChatMessages() = test { client ->
    val response =
      client.get("/chats/${chatOne.id}/messages?page=1&perPage=25") {
        header("Cookie", userAccessToken())
      }

    assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<CountedList<MessageGroupAggregate>>()
    assertEquals(2, body.items.size)

    // Message groups are sorted by createdAt DESC

    assertEquals(messageGroupOne.group.id, body.items[0].group.id)
    assertEquals(messageGroupOne.messages[0].id, body.items[0].messages[0].id)
    assertEquals(messageGroupOne.messages[1].id, body.items[0].messages[1].id)

    assertEquals(messageGroupTwo.group.id, body.items[1].group.id)
    assertEquals(messageGroupTwo.messages[0].id, body.items[1].messages[0].id)
    assertEquals(messageGroupTwo.messages[1].id, body.items[1].messages[1].id)
  }

  @Test
  fun shouldThrowErrorWhenRetrievingMessagesForNonExistingChat() = test { client ->
    val response =
      client.get("/chats/${UUID.randomUUID()}/messages?page=1&perPage=25") {
        header("Cookie", userAccessToken())
      }

    assertEquals(404, response.status.value)
  }

  @Test
  fun shouldEvaluateMessage() = test { client ->
    val evaluation = EvaluateMessage(true)
    val response =
      client.patch("/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header("Cookie", userAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }

    assertEquals(HttpStatusCode.NoContent, response.status)

    val messages =
      client
        .get("/chats/${chatOne.id}/messages?page=1&perPage=25") {
          header(HttpHeaders.Cookie, userAccessToken())
        }
        .body<CountedList<MessageGroupAggregate>>()

    assertEquals(2, messages.items.size)
    assertEquals(true, messages.items[1].evaluation?.evaluation)
    assertEquals(null, messages.items[1].evaluation?.feedback)
  }

  @Test
  fun shouldEvaluateMessageWithFeedback() = test { client ->
    val evaluation = EvaluateMessage(true, PropertyUpdate.Value("good job"))
    val response =
      client.patch("/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header("Cookie", userAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }

    assertEquals(HttpStatusCode.NoContent, response.status)

    val messages =
      client
        .get("/chats/${chatOne.id}/messages?page=1&perPage=25") {
          header(HttpHeaders.Cookie, userAccessToken())
        }
        .body<CountedList<MessageGroupAggregate>>()

    assertEquals(2, messages.items.size)
    assertEquals(true, messages.items[1].evaluation?.evaluation)
    assertEquals("good job", messages.items[1].evaluation?.feedback)

    assertEquals(null, messages.items[0].evaluation)
  }

  @Test
  fun shouldRemoveEvaluateMessage() = test { client ->
    val evaluationOne = EvaluateMessage(true, PropertyUpdate.Value("what a marvelous response"))
    val responseEvaluation =
      client.patch("/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header(HttpHeaders.Cookie, userAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluationOne)
      }

    assertEquals(HttpStatusCode.NoContent, responseEvaluation.status)

    val responseEvaluationCheck =
      client.get("/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, userAccessToken())
      }

    assertEquals(HttpStatusCode.OK, responseEvaluationCheck.status)

    val evaluationCheckBody = responseEvaluationCheck.body<CountedList<MessageGroupAggregate>>()

    assertEquals(1, evaluationCheckBody.items.size)
    assertEquals(messageGroupTwo.group.id, evaluationCheckBody.items[0].group.id)
    assertEquals(true, evaluationCheckBody.items[0].evaluation!!.evaluation)
    assertEquals("what a marvelous response", evaluationCheckBody.items[0].evaluation!!.feedback)

    val evaluationRemove = EvaluateMessage()
    val responseRemoveEvaluation =
      client.patch("/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header(HttpHeaders.Cookie, userAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluationRemove)
      }

    assertEquals(HttpStatusCode.NoContent, responseRemoveEvaluation.status)

    val responseRemoveEvaluationCheck =
      client.get("/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, userAccessToken())
      }

    assertEquals(HttpStatusCode.OK, responseRemoveEvaluationCheck.status)

    val removeEvaluationCheckBody =
      responseRemoveEvaluationCheck.body<CountedList<MessageGroupAggregate>>()

    assertEquals(1, removeEvaluationCheckBody.items.size)
    assertEquals(messageGroupTwo.group.id, removeEvaluationCheckBody.items[0].group.id)
    assertEquals(null, removeEvaluationCheckBody.items[0].evaluation)
  }

  @Test
  fun shouldRetrieveChatWithAgent() = test { client ->
    val response = client.get("/chats/${chatOne.id}") { header("Cookie", userAccessToken()) }

    assertEquals(200, response.status.value)
    val body = response.body<ChatWithAgent>()
    assertEquals(chatOne.id, body.chat.id)
    assertEquals(agent.id, body.agent.id)
  }
}
