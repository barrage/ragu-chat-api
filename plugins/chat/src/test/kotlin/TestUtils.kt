import io.ktor.client.HttpClient
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import java.util.*
import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import model.AgentCollection
import model.toAgentCollection
import net.barrage.llmao.core.AppError
import net.barrage.llmao.core.llm.FinishReason
import net.barrage.llmao.core.model.MessageGroupAggregate
import net.barrage.llmao.core.model.User
import net.barrage.llmao.core.model.toMessage
import net.barrage.llmao.core.model.toMessageGroup
import net.barrage.llmao.core.model.toMessageGroupEvaluation
import net.barrage.llmao.core.types.KUUID
import net.barrage.llmao.core.workflow.StreamChunk
import net.barrage.llmao.core.workflow.StreamComplete
import net.barrage.llmao.tables.references.AGENTS
import net.barrage.llmao.tables.references.AGENT_COLLECTIONS
import net.barrage.llmao.tables.references.AGENT_CONFIGURATIONS
import net.barrage.llmao.tables.references.AGENT_PERMISSIONS
import net.barrage.llmao.tables.references.APPLICATION_SETTINGS
import net.barrage.llmao.tables.references.CHATS
import net.barrage.llmao.tables.references.MESSAGES
import net.barrage.llmao.tables.references.MESSAGE_GROUPS
import net.barrage.llmao.tables.references.MESSAGE_GROUP_EVALUATIONS
import net.barrage.llmao.tables.references.WHATS_APP_NUMBERS
import net.barrage.llmao.test.ADMIN_USER
import net.barrage.llmao.test.TestPostgres
import net.barrage.llmao.test.adminWsSession
import net.barrage.llmao.test.openExistingWorkflow
import net.barrage.llmao.test.openNewWorkflow
import net.barrage.llmao.test.sendMessage

private val json = Json { ignoreUnknownKeys = true }

suspend fun TestPostgres.deleteTestAgent(id: UUID) {
  dslContext.deleteFrom(AGENTS).where(AGENTS.ID.eq(id)).awaitSingle()
}

suspend fun TestPostgres.testAgent(
  name: String = "Test",
  active: Boolean = true,
  groups: List<String>? = null,
): Agent {
  val agent =
    dslContext
      .insertInto(AGENTS)
      .columns(AGENTS.NAME, AGENTS.DESCRIPTION, AGENTS.ACTIVE, AGENTS.ACTIVE, AGENTS.LANGUAGE)
      .values(name, "Test", active, active, "croatian")
      .returning()
      .awaitSingle()
      .toAgent()

  groups?.let {
    dslContext
      .insertInto(AGENT_PERMISSIONS)
      .columns(AGENT_PERMISSIONS.AGENT_ID, AGENT_PERMISSIONS.GROUP, AGENT_PERMISSIONS.CREATED_BY)
      .apply { groups.forEach { group -> values(agent.id, group, ADMIN_USER.id) } }
      .awaitSingle()
  }

  return agent
}

suspend fun TestPostgres.testAgentConfiguration(
  agentId: UUID,
  version: Int = 1,
  context: String = "Test",
  llmProvider: String = "openai",
  model: String = "gpt-4o-mini",
  temperature: Double = 0.1,
  presencePenalty: Double? = null,
  titleInstruction: String? = null,
  errorMessage: String? = null,
): AgentConfiguration {
  val configuration =
    dslContext
      .insertInto(AGENT_CONFIGURATIONS)
      .columns(
        AGENT_CONFIGURATIONS.AGENT_ID,
        AGENT_CONFIGURATIONS.VERSION,
        AGENT_CONFIGURATIONS.CONTEXT,
        AGENT_CONFIGURATIONS.LLM_PROVIDER,
        AGENT_CONFIGURATIONS.MODEL,
        AGENT_CONFIGURATIONS.TEMPERATURE,
        AGENT_CONFIGURATIONS.PRESENCE_PENALTY,
        AGENT_CONFIGURATIONS.TITLE_INSTRUCTION,
        AGENT_CONFIGURATIONS.ERROR_MESSAGE,
      )
      .values(
        agentId,
        version,
        context,
        llmProvider,
        model,
        temperature,
        presencePenalty,
        titleInstruction,
        errorMessage,
      )
      .returning()
      .awaitSingle()
      .toAgentConfiguration()

  dslContext
    .update(AGENTS)
    .set(AGENTS.ACTIVE_CONFIGURATION_ID, configuration.id)
    .where(AGENTS.ID.eq(agentId))
    .awaitSingle()

  return configuration
}

suspend fun TestPostgres.testAgentCollection(
  agentId: UUID,
  collection: String,
  amount: Int,
  instruction: String,
  embeddingProvider: String = "azure",
  embeddingModel: String = "text-embedding-ada-002",
  vectorProvider: String,
): AgentCollection {
  return dslContext
    .insertInto(AGENT_COLLECTIONS)
    .set(AGENT_COLLECTIONS.AGENT_ID, agentId)
    .set(AGENT_COLLECTIONS.COLLECTION, collection)
    .set(AGENT_COLLECTIONS.AMOUNT, amount)
    .set(AGENT_COLLECTIONS.INSTRUCTION, instruction)
    .set(AGENT_COLLECTIONS.EMBEDDING_PROVIDER, embeddingProvider)
    .set(AGENT_COLLECTIONS.EMBEDDING_MODEL, embeddingModel)
    .set(AGENT_COLLECTIONS.VECTOR_PROVIDER, vectorProvider)
    .returning()
    .awaitSingle()
    .toAgentCollection()
}

suspend fun TestPostgres.testChat(
  user: User,
  agentId: UUID,
  agentConfigurationId: UUID,
  title: String? = "Test Chat Title",
  type: String = "CHAT",
): Chat {
  return dslContext
    .insertInto(CHATS)
    .set(CHATS.ID, UUID.randomUUID())
    .set(CHATS.USER_ID, user.id)
    .set(CHATS.USERNAME, user.username)
    .set(CHATS.AGENT_ID, agentId)
    .set(CHATS.AGENT_CONFIGURATION_ID, agentConfigurationId)
    .set(CHATS.TITLE, title)
    .set(CHATS.TYPE, type)
    .returning()
    .awaitSingle()
    .toChat()
}

suspend fun TestPostgres.deleteTestChat(id: UUID) {
  dslContext.deleteFrom(CHATS).where(CHATS.ID.eq(id)).awaitSingle()
}

suspend fun TestPostgres.testMessagePair(
  chatId: UUID,
  userContent: String = "Test message",
  assistantContent: String = "Test response",
  evaluation: Boolean? = null,
  feedback: String? = null,
): MessageGroupAggregate {
  val messageGroup =
    dslContext
      .insertInto(MESSAGE_GROUPS)
      .set(MESSAGE_GROUPS.PARENT_ID, chatId)
      .set(MESSAGE_GROUPS.PARENT_TYPE, CHAT_WORKFLOW_ID)
      .returning()
      .awaitSingle()

  val userMessage =
    dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.MESSAGE_GROUP_ID, messageGroup.id)
      .set(MESSAGES.ORDER, 0)
      .set(MESSAGES.SENDER_TYPE, "user")
      .set(MESSAGES.CONTENT, userContent)
      .returning()
      .awaitSingle()
      .toMessage()

  val assistantMessage =
    dslContext
      .insertInto(MESSAGES)
      .set(MESSAGES.MESSAGE_GROUP_ID, messageGroup.id)
      .set(MESSAGES.ORDER, 1)
      .set(MESSAGES.SENDER_TYPE, "assistant")
      .set(MESSAGES.CONTENT, assistantContent)
      .set(MESSAGES.FINISH_REASON, FinishReason.Stop.value)
      .returning()
      .awaitSingle()
      .toMessage()

  val evaluated =
    evaluation?.let {
      dslContext
        .insertInto(MESSAGE_GROUP_EVALUATIONS)
        .set(MESSAGE_GROUP_EVALUATIONS.MESSAGE_GROUP_ID, messageGroup.id)
        .set(MESSAGE_GROUP_EVALUATIONS.EVALUATION, evaluation)
        .set(MESSAGE_GROUP_EVALUATIONS.FEEDBACK, feedback)
        .returning()
        .awaitSingle()
    }

  return MessageGroupAggregate(
    group = messageGroup.toMessageGroup(),
    messages = mutableListOf(userMessage, assistantMessage),
    evaluation = evaluated?.toMessageGroupEvaluation(),
  )
}

suspend fun TestPostgres.testWhatsAppNumber(user: User, phoneNumber: String): WhatsAppNumber {
  return dslContext
    .insertInto(WHATS_APP_NUMBERS)
    .set(WHATS_APP_NUMBERS.USER_ID, user.id)
    .set(WHATS_APP_NUMBERS.USERNAME, user.username)
    .set(WHATS_APP_NUMBERS.PHONE_NUMBER, phoneNumber)
    .returning()
    .awaitSingle()
    .toWhatsAppNumber()
}

suspend fun TestPostgres.deleteTestWhatsAppNumber(id: UUID) {
  dslContext.deleteFrom(WHATS_APP_NUMBERS).where(WHATS_APP_NUMBERS.ID.eq(id)).awaitSingle()
}

suspend fun TestPostgres.setWhatsAppAgent(agentId: KUUID) {
  dslContext
    .insertInto(APPLICATION_SETTINGS)
    .set(APPLICATION_SETTINGS.NAME, WhatsappAgentId.KEY)
    .set(APPLICATION_SETTINGS.VALUE, agentId.toString())
    .onConflict(APPLICATION_SETTINGS.NAME)
    .doUpdate()
    .set(APPLICATION_SETTINGS.VALUE, agentId.toString())
    .awaitSingle()
}

suspend fun TestPostgres.deleteWhatsAppAgent() {
  dslContext
    .deleteFrom(APPLICATION_SETTINGS)
    .where(APPLICATION_SETTINGS.NAME.eq(WhatsappAgentId.KEY))
    .awaitSingle()
}

/** Open a new chat, send a message and collect the response. */
suspend fun HttpClient.openSendAndCollect(
  agentId: KUUID? = null,
  chatId: KUUID? = null,
  message: String,
): Pair<KUUID, String> {
  var buffer = ""
  lateinit var openChatId: KUUID

  adminWsSession {
    openChatId =
      agentId?.let { openNewWorkflow(CHAT_WORKFLOW_ID, Json.encodeToJsonElement(NewChatWorkflowParameters(agentId))) }
        ?: chatId?.let { openExistingWorkflow(it, CHAT_WORKFLOW_ID) }
        ?: throw IllegalArgumentException("Must provide either agentId or chatId")

    sendMessage(message) { incoming ->
      for (frame in incoming) {
        val response = (frame as Frame.Text).readText()
        try {
          val message = json.decodeFromString<StreamChunk>(response)
          buffer += message.chunk
        } catch (_: SerializationException) {}

        try {
          val message = json.decodeFromString<StreamComplete>(response)
          assert(message.reason == FinishReason.Stop)
          break
        } catch (_: SerializationException) {}

        try {
          val message = json.decodeFromString<AppError>(response)
          throw message
          break
        } catch (_: SerializationException) {}
      }
    }
  }

  return Pair(openChatId, buffer)
}
