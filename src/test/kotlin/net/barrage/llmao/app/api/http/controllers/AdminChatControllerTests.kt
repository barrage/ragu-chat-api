package net.barrage.llmao.app.api.http.controllers

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.*
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.ADMIN_USER
import net.barrage.llmao.IntegrationTest
import net.barrage.llmao.USER_USER
import net.barrage.llmao.adminAccessToken
import net.barrage.llmao.app.api.http.dto.UpdateChatTitleDTO
import net.barrage.llmao.app.workflow.chat.model.Agent
import net.barrage.llmao.app.workflow.chat.model.AgentConfiguration
import net.barrage.llmao.core.ValidationError
import net.barrage.llmao.core.model.Chat
import net.barrage.llmao.core.model.ChatWithAgent
import net.barrage.llmao.core.model.EvaluateMessage
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.common.CountedList
import net.barrage.llmao.core.model.common.PropertyUpdate
import net.barrage.llmao.core.repository.ChatRepositoryRead
import net.barrage.llmao.core.repository.ChatRepositoryWrite
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class AdminChatControllerTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration
  private lateinit var chatOne: Chat
  private lateinit var chatTwo: Chat
  private lateinit var chatThree: Chat
  private lateinit var messageGroupOne: MessageGroupAggregate
  private lateinit var messageGroupTwo: MessageGroupAggregate
  private lateinit var chatRepositoryWrite: ChatRepositoryWrite
  private lateinit var chatRepositoryRead: ChatRepositoryRead

  @BeforeAll
  fun setup() {
    runBlocking {
      agent = postgres.testAgent(active = true)
      agentConfiguration = postgres.testAgentConfiguration(agent.id)

      chatOne = postgres.testChat(USER_USER, agent.id)
      chatTwo = postgres.testChat(USER_USER, agent.id)
      chatThree = postgres.testChat(ADMIN_USER, agent.id)

      messageGroupOne =
        postgres.testMessagePair(
          chatId = chatOne.id,
          agentConfigurationId = agentConfiguration.id,
          userContent = "First Message",
          assistantContent = "First Response",
        )
      messageGroupTwo =
        postgres.testMessagePair(
          chatId = chatOne.id,
          agentConfigurationId = agentConfiguration.id,
          userContent = "Second Message",
          assistantContent = "Second Response",
        )
      chatRepositoryWrite = ChatRepositoryWrite(postgres.dslContext, "CHAT")
      chatRepositoryRead = ChatRepositoryRead(postgres.dslContext, "CHAT")
    }
  }

  @Test
  fun shouldRetrieveAllChatsDefaultPagination() = test { client ->
    val response = client.get("/admin/chats") { header(HttpHeaders.Cookie, adminAccessToken()) }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithAgent>>()
    assertNotNull(body)
    assertEquals(3, body.total)
    assertEquals(3, body.items.size)
  }

  @Test
  fun shouldRetrieveAllChatsFilterByUserId() = test { client ->
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, adminAccessToken())
        parameter("userId", USER_USER.id)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithAgent>>()
    assertNotNull(body)
    assertEquals(2, body.total)
    assertEquals(2, body.items.size)
  }

  @Test
  fun shouldRetrieveAllChatsFilterByAgentId() = test { client ->
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, adminAccessToken())
        parameter("agentId", agent.id)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithAgent>>()
    assertNotNull(body)
    assertEquals(3, body.total)
    assertEquals(3, body.items.size)
  }

  @Test
  fun shouldRetrieveAllChatsFilterByTitle() = test { client ->
    val response =
      client.get("/admin/chats") {
        header(HttpHeaders.Cookie, adminAccessToken())
        parameter("title", "Chat")
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<CountedList<ChatWithAgent>>()
    assertNotNull(body)
    assertEquals(3, body.total)
    assertEquals(3, body.items.size)
  }

  @Test
  fun shouldUpdateChatTitle() = test { client ->
    val newTitle = "Updated Chat Title"
    val updatedChatTitle = UpdateChatTitleDTO(newTitle)
    val response =
      client.put("/admin/chats/${chatOne.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updatedChatTitle)
      }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<Chat>()
    assertEquals(chatOne.id, body.id)
    assertEquals("Updated Chat Title", body.title)
  }

  @Test
  fun shouldUpdateChatTitleValidationFails() = test { client ->
    val updatedChatTitle = UpdateChatTitleDTO("")
    val response =
      client.put("/admin/chats/${chatOne.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(updatedChatTitle)
      }
    assertEquals(422, response.status.value)
    val body = response.body<List<ValidationError>>()
    assertEquals("title", body[0].fieldName)
  }

  @Test
  fun shouldRetrieveMessagesForChat() = test { client ->
    val response =
      client.get("/admin/chats/${chatOne.id}/messages?page=1&perPage=25") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }
    assertEquals(HttpStatusCode.OK, response.status)

    val body = response.body<CountedList<MessageGroupAggregate>>()

    assertEquals(2, body.items.size)
    assertEquals(2, body.total)

    val groupOnePrompt = messageGroupOne.messages[0]
    val groupOneResponse = messageGroupOne.messages[1]

    val groupTwoPrompt = messageGroupTwo.messages[0]
    val groupTwoResponse = messageGroupTwo.messages[1]

    // Message groups are sorted by createdAt DESC

    val checkGroupOne = body.items[0]
    val checkGroupTwo = body.items[1]

    assertEquals(messageGroupOne.group.id, checkGroupOne.group.id)

    assertEquals(groupOnePrompt.id, checkGroupOne.messages[0].id)
    assertEquals(groupOneResponse.id, checkGroupOne.messages[1].id)

    assertEquals(messageGroupTwo.group.id, checkGroupTwo.group.id)

    assertEquals(groupTwoPrompt.id, checkGroupTwo.messages[0].id)
    assertEquals(groupTwoResponse.id, checkGroupTwo.messages[1].id)
  }

  @Test
  fun shouldThrowErrorWhenRetrievingMessagesForNonExistingChat() = test { client ->
    val response =
      client.get("/admin/chats/${UUID.randomUUID()}/messages?page=1&perPage=25") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(HttpStatusCode.NotFound, response.status)
  }

  @Test
  fun shouldEvaluateMessageFromChat() = test { client ->
    val evaluation = EvaluateMessage(true)
    val response =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }
    assertEquals(HttpStatusCode.NoContent, response.status)

    val messages =
      client
        .get("/admin/chats/${chatOne.id}/messages?page=1&perPage=25") {
          header(HttpHeaders.Cookie, adminAccessToken())
        }
        .body<CountedList<MessageGroupAggregate>>()

    assertEquals(2, messages.items.size)
    assertEquals(true, messages.items[1].evaluation?.evaluation)
    assertEquals(null, messages.items[1].evaluation?.feedback)
  }

  @Test
  fun shouldEvaluateMessageFromChatWithFeedback() = test { client ->
    val evaluation = EvaluateMessage(true, PropertyUpdate.Value("oh yes, what a splendid response"))
    val response =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluation)
      }
    assertEquals(HttpStatusCode.NoContent, response.status)

    val messages =
      client
        .get("/admin/chats/${chatOne.id}/messages?page=1&perPage=25") {
          header(HttpHeaders.Cookie, adminAccessToken())
        }
        .body<CountedList<MessageGroupAggregate>>()

    assertEquals(2, messages.items.size)

    assertEquals(null, messages.items[0].evaluation)

    assertEquals(true, messages.items[1].evaluation?.evaluation)
    assertEquals("oh yes, what a splendid response", messages.items[1].evaluation?.feedback)
  }

  @Test
  fun shouldRemoveEvaluateMessageFromChat() = test { client ->
    val evaluationUpdate = EvaluateMessage(true, PropertyUpdate.Value("what a marvelous response"))
    val responseEvaluation =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluationUpdate)
      }

    assertEquals(HttpStatusCode.NoContent, responseEvaluation.status)

    val responseEvaluationCheck =
      client.get("/admin/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(HttpStatusCode.OK, responseEvaluationCheck.status)

    val evaluationCheckBody = responseEvaluationCheck.body<CountedList<MessageGroupAggregate>>()

    assertEquals(1, evaluationCheckBody.items.size)
    assertEquals(messageGroupTwo.group.id, evaluationCheckBody.items[0].group.id)
    assertEquals(true, evaluationCheckBody.items[0].evaluation!!.evaluation)
    assertEquals("what a marvelous response", evaluationCheckBody.items[0].evaluation!!.feedback)

    val evaluationRemove = EvaluateMessage(null)
    val responseRemoveEvaluation =
      client.patch("/admin/chats/${chatOne.id}/messages/${messageGroupTwo.group.id}") {
        header(HttpHeaders.Cookie, adminAccessToken())
        contentType(ContentType.Application.Json)
        setBody(evaluationRemove)
      }

    assertEquals(HttpStatusCode.NoContent, responseRemoveEvaluation.status)

    val responseRemoveEvaluationCheck =
      client.get("/admin/chats/${chatOne.id}/messages?page=1&perPage=1") {
        header(HttpHeaders.Cookie, adminAccessToken())
      }

    assertEquals(HttpStatusCode.OK, responseRemoveEvaluationCheck.status)

    val removeEvaluationCheckBody =
      responseRemoveEvaluationCheck.body<CountedList<MessageGroupAggregate>>()

    assertEquals(1, removeEvaluationCheckBody.items.size)
    assertEquals(messageGroupTwo.group.id, removeEvaluationCheckBody.items[0].group.id)
    assertEquals(null, removeEvaluationCheckBody.items[0].evaluation)
  }

  @Test
  fun shouldGetChatWithUserAndAgent() = test { client ->
    val response =
      client.get("/admin/chats/${chatOne.id}") { header(HttpHeaders.Cookie, adminAccessToken()) }
    assertEquals(HttpStatusCode.OK, response.status)
    val body = response.body<ChatWithAgent>()
    assertEquals(chatOne.id, body.chat.id)
    assertEquals(USER_USER.id, body.chat.userId)
    assertEquals(agent.id, body.agent.id)
  }

  //  @Test
  //  fun shouldDeleteChatWithExistingMessages() = test {
  //    val newChat = postgres.testChat(USER_USER, agent.id)
  //    val chat = chatRepositoryRead.getWithMessages(newChat.id, Pagination(1, 200))!!
  //
  //    val userMessage =
  //      MessageInsert(
  //        id = UUID.randomUUID(),
  //        chatId = chat.chat.id,
  //        content = "Test Message",
  //        sender = user.id,
  //        senderType = "user",
  //        responseTo = null,
  //        finishReason = null,
  //      )
  //
  //    val agentMessage =
  //      MessageInsert(
  //        id = UUID.randomUUID(),
  //        chatId = chat.chat.id,
  //        content = "Test Message",
  //        sender = agent.id,
  //        senderType = "assistant",
  //        responseTo = userMessage.id,
  //        finishReason = FinishReason.Stop,
  //      )
  //
  //    val client = createClient { install(ContentNegotiation) { json() } }
  //    val response =
  //      client.delete("/admin/chats/${chat.chat.id}") {
  //        header(HttpHeaders.Cookie, adminAccessToken())
  //      }
  //
  //    assertEquals(HttpStatusCode.NoContent, response.status)
  //
  //    assertThrows<AppError> { chatRepositoryRead.getChatWithMessages(chat.chat.id) }
  //
  //    val deletedUserMessage = chatRepositoryRead.getMessage(chat.chat.id, userMessage.id)
  //    val deletedAgentMessage = chatRepositoryRead.getMessage(chat.chat.id, agentMessage.id)
  //
  //    assertNull(deletedUserMessage)
  //    assertNull(deletedAgentMessage)
  //  }
}
