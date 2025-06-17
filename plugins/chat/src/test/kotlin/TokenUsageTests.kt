import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import kotlin.test.assertEquals
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import net.barrage.llmao.core.token.TokenUsageAggregate
import net.barrage.llmao.core.token.TokenUsageType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TokenUsageTests : IntegrationTest() {
  private lateinit var agent: Agent
  private lateinit var agentConfiguration: AgentConfiguration

  @BeforeAll
  fun setup() {
    runBlocking {
      // agent = postgres.testAgent()
      agentConfiguration =
        postgres.testAgentConfiguration(
          agent.id,
          llmProvider = "openai",
          model = "gpt-4o",
          titleInstruction = COMPLETIONS_TITLE_PROMPT,
        )
    }
  }

  @Test
  fun registersUsageWhenCallingChatCompletion() = wsTest { client ->
    val (chatId, response) =
      client.openSendAndCollect(agentId = agent.id, message = COMPLETIONS_STREAM_PROMPT)
    assertEquals(COMPLETIONS_STREAM_RESPONSE, response)

    // Use delays since storing the usage is done asynchronously
    delay(200)
    val usage =
      client
        .get("/admin/tokens/usage") {
          header(HttpHeaders.Cookie, adminAccessToken())
          header(HttpHeaders.Accept, ContentType.Application.Json)
        }
        .body<TokenUsageAggregate>()

    val completionUsage =
      usage.usage["openai"]!!["gpt-4o"]!!.find { it.usageType == TokenUsageType.COMPLETION }!!

    assertEquals(CHAT_WORKFLOW_ID, completionUsage.workflowType)
    assertEquals(chatId, completionUsage.workflowId)
    assertEquals(ADMIN_USER.id, completionUsage.userId)

    val titleUsage =
      usage.usage["openai"]!!["gpt-4o"]!!.find { it.usageType == TokenUsageType.COMPLETION_TITLE }!!

    assertEquals(CHAT_WORKFLOW_ID, titleUsage.workflowType)
    assertEquals(chatId, titleUsage.workflowId)
    assertEquals(ADMIN_USER.id, titleUsage.userId)
  }
}
